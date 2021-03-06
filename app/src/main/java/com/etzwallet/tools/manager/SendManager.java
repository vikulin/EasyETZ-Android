package com.etzwallet.tools.manager;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.etzwallet.R;
import com.etzwallet.presenter.customviews.BRDialogView;
import com.etzwallet.presenter.entities.CryptoRequest;
import com.etzwallet.presenter.interfaces.BRAuthCompletion;
import com.etzwallet.tools.animation.BRAnimator;
import com.etzwallet.tools.animation.BRDialog;
import com.etzwallet.tools.security.AuthManager;
import com.etzwallet.tools.security.BRKeyStore;
import com.etzwallet.tools.security.PostAuth;
import com.etzwallet.tools.threads.executor.BRExecutor;
import com.etzwallet.tools.util.BRConstants;
import com.etzwallet.tools.util.CurrencyUtils;
import com.etzwallet.tools.util.Utils;
import com.etzwallet.wallet.WalletsMaster;
import com.etzwallet.wallet.abstracts.BaseWalletManager;
import com.etzwallet.wallet.exceptions.AmountSmallerThanMinException;
import com.etzwallet.wallet.exceptions.FeeNeedsAdjust;
import com.etzwallet.wallet.exceptions.FeeOutOfDate;
import com.etzwallet.wallet.exceptions.InsufficientFundsException;
import com.etzwallet.wallet.exceptions.SomethingWentWrong;
import com.etzwallet.wallet.exceptions.SpendingNotAllowed;
import com.etzwallet.wallet.wallets.ethereum.WalletEthManager;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan on <mihail@breadwallet.com> 2/20/18.
 * Copyright (c) 2018 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class SendManager {
    private static final String TAG = SendManager.class.getSimpleName();

    private static boolean timedOut;
    private static boolean sending;
    private final static long FEE_EXPIRATION_MILLIS = 72 * 60 * 60 * 1000L;


    @WorkerThread
    public static boolean sendTransaction(final Context app, final CryptoRequest payment, final BaseWalletManager walletManager, final SendCompletion completion) {
        try {
            if (sending) {
                Log.e(TAG, "sendTransaction: already sending..");
                return false;
            }
            sending = true;
            long now = System.currentTimeMillis();
            //if the fee (for BTC and BCH only) was updated more than 24 hours ago then try updating the fee
            if (walletManager.getIso().equalsIgnoreCase("BTC") || walletManager.getIso().equalsIgnoreCase("BCH")) {
                if (now - BRSharedPrefs.getFeeTime(app, walletManager.getIso()) >= FEE_EXPIRATION_MILLIS) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            if (sending) timedOut = true;
                        }
                    }).start();
                    walletManager.updateFee(app);
                    //if the fee is STILL out of date then fail with network problem message
                    long time = BRSharedPrefs.getFeeTime(app, walletManager.getIso());
                    if (time <= 0 || now - time >= FEE_EXPIRATION_MILLIS) {
                        Log.e(TAG, "sendTransaction: fee out of date even after fetching...");
                        throw new FeeOutOfDate(BRSharedPrefs.getFeeTime(app, walletManager.getIso()), now);
                    }
                }
            }
            if (!timedOut)
                tryPay(app, payment, walletManager, completion);
            else
                BRReportsManager.reportBug(new NullPointerException("did not send, timedOut!"));
            return true; //return so no error is shown
        } catch (InsufficientFundsException ignored) {
            BigDecimal fee = walletManager.getEstimatedFee(payment.amount, "");
            if (WalletsMaster.getInstance(app).isIsoErc20(app, walletManager.getIso()) &&
                    fee.compareTo(WalletEthManager.getInstance(app).getCachedBalance(app)) > 0) {
                sayError(app, app.getString(R.string.Send_insufficientGasTitle), String.format(app.getString(R.string.Send_insufficientGasMessage), CurrencyUtils.getFormattedAmount(app, "ETZ", fee)));
            } else
                sayError(app, app.getString(R.string.Alerts_sendFailure), app.getString(R.string.Send_insufficientFunds));
        } catch (AmountSmallerThanMinException e) {
            BigDecimal minAmount = walletManager.getMinOutputAmount(app);
            sayError(app, app.getString(R.string.Alerts_sendFailure), String.format(Locale.getDefault(), app.getString(R.string.PaymentProtocol_Errors_smallPayment),
                    BRConstants.BITS_SYMBOL + minAmount.divide(new BigDecimal(100), BRConstants.ROUNDING_MODE)));
        } catch (SpendingNotAllowed spendingNotAllowed) {
            sayError(app, app.getString(R.string.Alert_error), app.getString(R.string.Send_isRescanning));
            return false;
        } catch (FeeNeedsAdjust feeNeedsAdjust) {
            //offer to change amount, so it would be enough for fee
//                    showFailed(app); //just show failed for now
            showAdjustFee((Activity) app, payment, walletManager, completion);
            return false;
        } catch (FeeOutOfDate ex) {
            //Fee is out of date, show not connected error
            BRReportsManager.reportBug(ex);
            sayError(app, app.getString(R.string.Alerts_sendFailure), app.getString(R.string.NodeSelector_notConnected));
            return false;
        } catch (SomethingWentWrong somethingWentWrong) {
            somethingWentWrong.printStackTrace();
            BRReportsManager.reportBug(somethingWentWrong);
            sayError(app, app.getString(R.string.Alerts_sendFailure), "Something went wrong:\n" + somethingWentWrong.getMessage());
            return false;
        } finally {
            sending = false;
            timedOut = false;
        }

        return true;
    }

    private static void sayError(final Context app, final String title, final String message) {
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                BRDialog.showSimpleDialog(app, title, message);
            }
        });
    }

    /**
     * Try transaction and throw appropriate exceptions if something was wrong
     * BLOCKS
     */
    private static void tryPay(final Context app, final CryptoRequest paymentRequest, final BaseWalletManager walletManager, final SendCompletion completion)
            throws InsufficientFundsException,
            AmountSmallerThanMinException, SpendingNotAllowed, FeeNeedsAdjust, SomethingWentWrong {
        if (paymentRequest == null) {
            Log.e(TAG, "tryPay: ERROR: paymentRequest: null");
            String message = "paymentRequest is null";
            BRReportsManager.reportBug(new RuntimeException("paymentRequest is malformed: " + message), true);
            throw new SomethingWentWrong("wrong parameters: paymentRequest");
        }

        Log.i(TAG, "tryPay: dataValue==="+paymentRequest.data);

        BigDecimal balance = walletManager.getCachedBalance(app);
        BigDecimal minOutputAmount = walletManager.getMinOutputAmount(app);

        //not enough for fee
        if (paymentRequest.notEnoughForFee(app, walletManager)) {
            throw new InsufficientFundsException(paymentRequest.amount, balance);
        }

        if (paymentRequest.feeOverBalance(app, walletManager)) {
            throw new FeeNeedsAdjust(paymentRequest.amount, balance, new BigDecimal(-1));
        }

        // check if spending is allowed
        if (!BRSharedPrefs.getAllowSpend(app, walletManager.getIso())) {
            throw new SpendingNotAllowed();
        }

        //check if amount isn't smaller than the min amount
        if (paymentRequest.isSmallerThanMin(app, walletManager)) {
            throw new AmountSmallerThanMinException(paymentRequest.amount, minOutputAmount);
        }

        //amount is larger than balance
        if (paymentRequest.isLargerThanBalance(app, walletManager)) {
            throw new InsufficientFundsException(paymentRequest.amount, balance);
        }

        // payment successful
        PostAuth.getInstance().setPaymentItem(paymentRequest);//将paymentRequest所有参数传入 PostAuth.java
        confirmPay(app, paymentRequest, walletManager, completion);

    }

    private static void showAdjustFee(final Activity app, final CryptoRequest item, final BaseWalletManager walletManager, final SendCompletion completion) {
        BaseWalletManager wm = WalletsMaster.getInstance(app).getCurrentWallet(app);
        BigDecimal maxAmountDouble = walletManager.getMaxOutputAmount(app);
        if (maxAmountDouble.compareTo(new BigDecimal(-1)) == 0) {
            BRReportsManager.reportBug(new RuntimeException("getMaxOutputAmount is -1, meaning _wallet is NULL"));
            return;
        }
        if (maxAmountDouble.compareTo(BigDecimal.ZERO) == 0) {
            BRDialog.showCustomDialog(app, app.getString(R.string.Alerts_sendFailure), app.getString(R.string.Send_nilFeeError), app.getString(R.string.Button_ok), null, new BRDialogView.BROnClickListener() {
                @Override
                public void onClick(BRDialogView brDialogView) {
                    brDialogView.dismissWithAnimation();
                }
            }, null, null, 0);
        } else {
            if (Utils.isNullOrEmpty(item.address)) throw new RuntimeException("can't happen");
            BigDecimal fee = wm.getEstimatedFee(maxAmountDouble, item.address);
            if (fee.compareTo(BigDecimal.ZERO) < 0) {
                BRReportsManager.reportBug(new RuntimeException("fee is weird:  " + fee));
                BRDialog.showCustomDialog(app, app.getString(R.string.Alerts_sendFailure), app.getString(R.string.Send_nilFeeError), app.getString(R.string.Button_ok), null, new BRDialogView.BROnClickListener() {
                    @Override
                    public void onClick(BRDialogView brDialogView) {
                        brDialogView.dismissWithAnimation();
                    }
                }, null, null, 0);
                return;
            }

            String formattedCrypto = CurrencyUtils.getFormattedAmount(app, wm.getIso(), maxAmountDouble.negate());
            String formattedFiat = CurrencyUtils.getFormattedAmount(app, BRSharedPrefs.getPreferredFiatIso(app), wm.getFiatForSmallestCrypto(app, maxAmountDouble, null).negate());

            String posButtonText = String.format("%s (%s)", formattedCrypto, formattedFiat);

            item.amount = maxAmountDouble;

            BRDialog.showCustomDialog(app, app.getString(R.string.Send_nilFeeError), "Send max?", posButtonText, "No thanks", new BRDialogView.BROnClickListener() {
                @Override
                public void onClick(BRDialogView brDialogView) {
                    brDialogView.dismissWithAnimation();
                    PostAuth.getInstance().setPaymentItem(item);
                    confirmPay(app, item, walletManager, completion);

                }
            }, new BRDialogView.BROnClickListener() {
                @Override
                public void onClick(BRDialogView brDialogView) {
                    brDialogView.dismissWithAnimation();
                }
            }, null, 0);
        }

    }

    //点击 转账 （还未输入密码）
    private static void confirmPay(final Context ctx, final CryptoRequest request, final BaseWalletManager wm, final SendCompletion completion) {
        if (ctx == null) {
            Log.e(TAG, "confirmPay: context is null");
            return;
        }

        String message = createConfirmation(ctx, request, wm);
        if (message == null) {
            BRDialog.showSimpleDialog(ctx, "Failed", "Confirmation message failed");
            return;
        }

//        BigDecimal minOutput = request.isAmountRequested ? wm.getMinOutputAmountPossible() : wm.getMinOutputAmount(ctx);
        BigDecimal minOutput = new BigDecimal("0");
        //amount can't be less than the min
        if (minOutput != null && request.amount.abs().compareTo(minOutput) < 0) {
            final String bitcoinMinMessage = String.format(Locale.getDefault(), ctx.getString(R.string.PaymentProtocol_Errors_smallTransaction),
                    CurrencyUtils.getFormattedAmount(ctx, wm.getIso(), minOutput));

            ((Activity) ctx).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BRDialog.showCustomDialog(ctx, ctx.getString(R.string.Alerts_sendFailure), bitcoinMinMessage, ctx.getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismiss();
                        }
                    }, null, null, 0);
                }
            });
            return;
        }
        boolean forcePin = false;

        if (Utils.isEmulatorOrDebug(ctx)) {
            Log.e(TAG, "confirmPay: totalSent: " + wm.getTotalSent(ctx));
            Log.e(TAG, "confirmPay: request.amount: " + request.amount);
            Log.e(TAG, "confirmPay: total limit: " + BRKeyStore.getTotalLimit(ctx, wm.getIso()));
            Log.e(TAG, "confirmPay: limit: " + BRKeyStore.getSpendLimit(ctx, wm.getIso()));
        }

        if (wm.getTotalSent(ctx).add(request.amount).compareTo(BRKeyStore.getTotalLimit(ctx, wm.getIso())) > 0) {
            forcePin = true;
        }


        final boolean isErc20 = WalletsMaster.getInstance(ctx).isIsoErc20(ctx, wm.getIso());

        //successfully created the transaction, authenticate user  弹出输入支付密码modal
        AuthManager.getInstance().authPrompt(ctx, ctx.getString(R.string.VerifyPin_touchIdMessage), message, forcePin, false, new BRAuthCompletion() {

            @Override
            public void onComplete() {
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        PostAuth.getInstance().onPublishTxAuth(ctx, wm, false, completion, request.data,isErc20);
                        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                            @Override
                            public void run() {
                                BRAnimator.killAllFragments((Activity) ctx);
                            }
                        });

                    }
                });

            }

            @Override
            public void onCancel() {
                //nothing
            }
        });

    }

    private static String createConfirmation(Context ctx, CryptoRequest request, final BaseWalletManager wm) {

        String receiver;
        boolean certified = false;
        if (request.cn != null && request.cn.length() != 0) {
            certified = true;
        }
        receiver = wm.decorateAddress(request.address);
        if (certified) {
            receiver = "certified: " + request.cn + "\n";
        }

        String iso = BRSharedPrefs.getPreferredFiatIso(ctx);
//        BigDecimal feeForTx = wm.getEstimatedFee(request.amount, request.address);
        BigDecimal feeForTx = new BigDecimal("0.00");
        if (feeForTx.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal maxAmount = wm.getMaxOutputAmount(ctx);
            if (maxAmount != null && maxAmount.compareTo(new BigDecimal(-1)) == 0) {
                BRReportsManager.reportBug(new RuntimeException("getMaxOutputAmount is -1, meaning _wallet is NULL"), true);
            }
            if (maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) == 0) {
                BRDialog.showCustomDialog(ctx, "", ctx.getString(R.string.Alerts_sendFailure),
                        ctx.getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                            @Override
                            public void onClick(BRDialogView brDialogView) {
                                brDialogView.dismiss();
                            }
                        }, null, null, 0);

                return null;
            }
        }
        if (feeForTx.compareTo(BigDecimal.ZERO) < 0) {
            BRDialog.showCustomDialog(ctx, "", ctx.getString(R.string.Send_nilFeeError),
                    ctx.getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismiss();
                        }
                    }, null, null, 0);
            return null;
        }
        BigDecimal amount = request.amount.abs();
        final BigDecimal total = amount.add(feeForTx);
        String formattedCryptoAmount = CurrencyUtils.getFormattedAmount(ctx, wm.getIso(), amount);
        String formattedCryptoFee = CurrencyUtils.getFormattedAmount(ctx, wm.getIso(), feeForTx);
        String formattedCryptoTotal = CurrencyUtils.getFormattedAmount(ctx, wm.getIso(), amount);//total

        String formattedAmount = CurrencyUtils.getFormattedAmount(ctx, iso, wm.getFiatForSmallestCrypto(ctx, amount, null));
        String formattedFee = CurrencyUtils.getFormattedAmount(ctx, iso, wm.getFiatForSmallestCrypto(ctx, feeForTx, null));
        String formattedTotal = CurrencyUtils.getFormattedAmount(ctx, iso, wm.getFiatForSmallestCrypto(ctx, amount, null));//total
//        Log.i(TAG, "createConfirmation: formattedFee==="+formattedFee);
//        Log.i(TAG, "createConfirmation: iso==="+iso);
//        Log.i(TAG, "createConfirmation: feeForTx==="+feeForTx);
//        Log.i(TAG, "createConfirmation: iso222==="+wm.getFiatForSmallestCrypto(ctx, feeForTx, null));
        boolean isErc20 = WalletsMaster.getInstance(ctx).isIsoErc20(ctx, wm.getIso());

        if (isErc20) {
            formattedCryptoTotal = "";
            formattedTotal = "";
            BaseWalletManager ethWm = WalletEthManager.getInstance(ctx);
            formattedCryptoFee = CurrencyUtils.getFormattedAmount(ctx, ethWm.getIso(), feeForTx);
            formattedFee = CurrencyUtils.getFormattedAmount(ctx, iso, ethWm.getFiatForSmallestCrypto(ctx, feeForTx, null));

        }

        String line1 = receiver + "\n\n";

        String line2 = ctx.getString(R.string.Confirmation_amountLabel) + " " + formattedCryptoAmount + " (" + formattedAmount + ")\n";
//        String line3 = ctx.getString(R.string.Confirmation_feeLabel) + " " + formattedCryptoFee + " (" + formattedFee + ")\n";//网络费用
        String line3 = ctx.getString(R.string.Confirmation_feeLabel) + " " + "0.00" + " (" + formattedFee + ")\n";//网络费用
        String line4 = ctx.getString(R.string.Confirmation_totalLabel) + " " + formattedCryptoTotal + " (" + formattedTotal + ")";//总费用
        String line5 = Utils.isNullOrEmpty(request.message) ? "" : "\n\n" + request.message;
//        String line6 = request.data;
        //formatted text
        return line1 + line2 + line3 + (isErc20 ? "" : line4) + line5;
    }

    public interface SendCompletion {
        void onCompleted(String hash, boolean succeed);
    }

}
