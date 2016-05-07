//==========================================================================
// 2015/08/31: yctung: add this new test for libSVM in jni interface 
//==========================================================================

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include "./libsvm/svm-train.h"
#include "./libsvm/svm-predict.h"
#include "common.h"
#include <android/log.h>


// helper function to be called in Java for making svm-train
extern "C" void Java_jakeparker_swerve_MotionSensor_jniSvmTrain(JNIEnv *env, jobject obj, jstring cmdIn)
{
	const char *cmd = env->GetStringUTFChars(cmdIn, 0);
	debug("jniSvmTrain cmd = %s", cmd);

	std::vector<char*> v;

	// add dummy head to meet argv/command format
	std::string cmdString = std::string("dummy ")+std::string(cmd);

	cmdToArgv(cmdString, v);

	// make svm train by libsvm
	svmtrain::main(v.size(),&v[0]);

    __android_log_print(ANDROID_LOG_DEBUG, "JNI_LIBSVM_TRAIN", "SUCCESS");

	// free vector memory
	for(int i=0;i<v.size();i++){
		free(v[i]);
	}

	// free java object memory
	env->ReleaseStringUTFChars(cmdIn, cmd);
}

// helper function to be called in Java for making svm-predict
extern "C" void Java_jakeparker_swerve_MotionSensor_jniSvmPredict(JNIEnv *env, jobject obj, jstring cmdIn)
{
	const char *cmd = env->GetStringUTFChars(cmdIn, 0);
	debug("jniSvmPredict cmd = %s", cmd);

	std::vector<char*> v;

	// add dummy head to meet argv/command format
	std::string cmdString = std::string("dummy ")+std::string(cmd);

	cmdToArgv(cmdString, v);

	// make svm train by libsvm
	svmpredict::main(v.size(),&v[0]);

    __android_log_print(ANDROID_LOG_DEBUG, "JNI_LIBSVM_PREDICT", "SUCCESS");

	// free vector memory
	for(int i=0;i<v.size();i++){
		free(v[i]);
	}

	// free java object memory
	env->ReleaseStringUTFChars(cmdIn, cmd);
}

extern "C" void Java_jakeparker_swerve_MotionSensor_jniHelloWorld(JNIEnv *env, jobject obj, jstring jstr)
{
    const char *cmd = env->GetStringUTFChars(jstr, 0); // arg[1]=JNI_FALSE
    __android_log_print(ANDROID_LOG_DEBUG, "JNI_TEST", "HELLO WORLD");
    env->ReleaseStringUTFChars(jstr, cmd); // arg[0]=jstring, arg[1]=cpp var pointing at jstr
}

/*
*  just some test functions -> can be removed
*/
extern "C" JNIEXPORT int JNICALL Java_jakeparker_swerve_MotionSensor_testInt(JNIEnv * env, jobject obj){
	return 5566;
}

extern "C" void Java_jakeparker_swerve_MotionSensor_testLog(JNIEnv *env, jobject obj, jstring logThis){
	const char * szLogThis = env->GetStringUTFChars(logThis, 0);
	debug("%s",szLogThis);

	env->ReleaseStringUTFChars(logThis, szLogThis);
}
