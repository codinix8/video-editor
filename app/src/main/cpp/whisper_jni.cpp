// JNI-Brücke zu whisper.cpp: Modell laden, PCM (16 kHz mono float) transkribieren,
// Ergebnis als JSON mit Sätzen und Wörtern (Zeitstempel in ms).
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <sstream>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct ProgressCtx { JNIEnv* env; jobject cb; jmethodID mid; };

static void progress_cb(struct whisper_context*, struct whisper_state*, int progress, void* user) {
    auto* p = static_cast<ProgressCtx*>(user);
    if (p && p->cb) p->env->CallVoidMethod(p->cb, p->mid, progress);
}

static std::string json_escape(const std::string& s) {
    std::string o; o.reserve(s.size() + 8);
    for (unsigned char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': break;
            case '\t': o += " "; break;
            default: if (c < 0x20) { char b[8]; snprintf(b, 8, "\\u%04x", c); o += b; } else o += (char)c;
        }
    }
    return o;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_de_codinix_videoeditor_whisper_WhisperEngine_nativeInit(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context* ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    LOGI("Modell geladen: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_de_codinix_videoeditor_whisper_WhisperEngine_nativeFree(JNIEnv*, jobject, jlong handle) {
    if (handle) whisper_free(reinterpret_cast<whisper_context*>(handle));
}

JNIEXPORT jstring JNICALL
Java_de_codinix_videoeditor_whisper_WhisperEngine_nativeTranscribe(
        JNIEnv* env, jobject, jlong handle, jfloatArray samples, jstring language,
        jint threads, jobject progressCb) {
    auto* ctx = reinterpret_cast<whisper_context*>(handle);
    if (!ctx) return env->NewStringUTF("{\"error\":\"no context\"}");

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.token_timestamps = true;
    params.max_len = 0;
    params.suppress_blank = true;
    params.suppress_nst = true;

    std::string lang;
    if (language) {
        const char* l = env->GetStringUTFChars(language, nullptr);
        lang = l; env->ReleaseStringUTFChars(language, l);
    }
    params.language = lang.empty() ? "auto" : lang.c_str();
    params.detect_language = false;

    ProgressCtx pc{env, progressCb, nullptr};
    if (progressCb) {
        jclass cls = env->GetObjectClass(progressCb);
        pc.mid = env->GetMethodID(cls, "onProgress", "(I)V");
        params.progress_callback = progress_cb;
        params.progress_callback_user_data = &pc;
    }

    int rc = whisper_full(ctx, params, pcm.data(), n);
    if (rc != 0) return env->NewStringUTF("{\"error\":\"whisper_full failed\"}");

    std::ostringstream js;
    js << "{\"language\":\"" << whisper_lang_str(whisper_full_lang_id(ctx)) << "\",\"segments\":[";
    int ns = whisper_full_n_segments(ctx);
    for (int i = 0; i < ns; i++) {
        int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10; // cs -> ms
        int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        std::string text = whisper_full_get_segment_text(ctx, i);
        if (i) js << ",";
        js << "{\"t0\":" << t0 << ",\"t1\":" << t1 << ",\"text\":\"" << json_escape(text) << "\",\"words\":[";
        // Tokens zu Wörtern zusammenfassen: ein Token mit führendem Leerzeichen beginnt ein neues Wort
        int nt = whisper_full_n_tokens(ctx, i);
        std::string word; int64_t w0 = -1, w1 = -1; bool first = true;
        auto flush = [&]() {
            if (!word.empty()) {
                if (!first) js << ",";
                first = false;
                js << "{\"t0\":" << w0 << ",\"t1\":" << w1 << ",\"w\":\"" << json_escape(word) << "\"}";
            }
            word.clear(); w0 = w1 = -1;
        };
        for (int t = 0; t < nt; t++) {
            whisper_token_data td = whisper_full_get_token_data(ctx, i, t);
            const char* tt = whisper_full_get_token_text(ctx, i, t);
            if (!tt || tt[0] == '[' || tt[0] == '<') continue; // Spezialtoken
            std::string s = tt;
            if (!s.empty() && s[0] == ' ') { flush(); s = s.substr(1); }
            if (word.empty()) w0 = td.t0 * 10;
            w1 = td.t1 * 10;
            word += s;
        }
        flush();
        js << "]}";
    }
    js << "]}";
    return env->NewStringUTF(js.str().c_str());
}

}
