/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_etzwallet_core_BRCorePaymentProtocolMessage */

#ifndef _Included_com_etzwallet_core_BRCorePaymentProtocolMessage
#define _Included_com_etzwallet_core_BRCorePaymentProtocolMessage
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    getMessageTypeValue
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_getMessageTypeValue
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    getMessage
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_getMessage
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    getStatusCode
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_getStatusCode
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    getStatusMessage
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_getStatusMessage
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    getIdentifier
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_getIdentifier
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    createPaymentProtocolMessage
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_createPaymentProtocolMessage
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    createPaymentProtocolMessageFull
 * Signature: (I[BJLjava/lang/String;[B)J
 */
JNIEXPORT jlong JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_createPaymentProtocolMessageFull
  (JNIEnv *, jclass, jint, jbyteArray, jlong, jstring, jbyteArray);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    serialize
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_serialize
  (JNIEnv *, jobject);

/*
 * Class:     com_etzwallet_core_BRCorePaymentProtocolMessage
 * Method:    disposeNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_etzwallet_core_BRCorePaymentProtocolMessage_disposeNative
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
