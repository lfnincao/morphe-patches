#include <jni.h>
#include <stdint.h>

/*
 * Harmless replacement for libmx-bh.so: satisfies libmxvp.so DT_NEEDED and exports
 * without verifier threads, JNI init side effects, or .init_array constructors.
 */

__attribute__((visibility("default"))) int bh_nn_3(int a, int b, int c, int d, void *e) {
    (void)a;
    (void)b;
    (void)c;
    (void)d;
    (void)e;
    return 0;
}

__attribute__((visibility("default"))) int bh_nn_4(int a, int b, int c, int d, void *e) {
    (void)a;
    (void)b;
    (void)c;
    (void)d;
    (void)e;
    return 0;
}

__attribute__((visibility("default"))) int bh_nn_5(int a, int b) {
    (void)a;
    (void)b;
    return 0;
}

/* libmxvp writes via `strb wzr, [buf, w0, sxtw]` using the return value as an index. */
__attribute__((visibility("default"))) int bh_nn_6(
    int a, jlong b, int c, void *buf, int d, int e) {
    (void)a;
    (void)b;
    (void)c;
    (void)buf;
    (void)d;
    (void)e;
    return 0;
}

JNIEXPORT void JNICALL Java_com_ddx_axx_axx_axx_BH_n(
    JNIEnv *env,
    jclass clazz,
    jobject context,
    jint i,
    jstring s1,
    jstring s2,
    jbyteArray bytes,
    jstring s3) {
    (void)env;
    (void)clazz;
    (void)context;
    (void)i;
    (void)s1;
    (void)s2;
    (void)bytes;
    (void)s3;
}

JNIEXPORT jboolean JNICALL Java_com_ddx_axx_axx_axx_BH_n1(JNIEnv *env, jclass clazz, jobject obj) {
    (void)env;
    (void)clazz;
    (void)obj;
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_ddx_axx_axx_axx_BH_t(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
}

JNIEXPORT void JNICALL Java_com_ddx_axx_axx_axx_BH_w(JNIEnv *env, jclass clazz, jobjectArray strings) {
    (void)env;
    (void)clazz;
    (void)strings;
}

JNIEXPORT jbyteArray JNICALL Java_com_ddx_axx_axx_axx_AS_e(
    JNIEnv *env, jclass clazz, jbyteArray input, jint offset, jboolean flag) {
    (void)env;
    (void)clazz;
    (void)input;
    (void)offset;
    (void)flag;
    return NULL;
}