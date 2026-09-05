// JNI surface of bergamot-translator for Mirrolit's "Offline HQ" engine.
// Built into libbergamot_jni.so by tools/bergamot/build.sh; see tools/bergamot/README.md.
//
// Contract: every entry point catches everything. A native throw must never abort the process —
// failures come back as 0 / nullptr with a message retrievable through lastError().
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "translator/parser.h"
#include "translator/response.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "translator/translation_model.h"

using namespace marian::bergamot;

namespace {

// BlockingService and TranslationModel are both documented not thread-safe. BergamotEngine
// serialises calls onto a single dispatcher already; this lock is belt and braces.
std::mutex gMutex;
std::string gLastError;
std::unique_ptr<BlockingService> gService;

BlockingService &service() {
  if (!gService) {
    BlockingService::Config cfg;
    cfg.cacheSize = 0;  // the app caches translations in Room; no native cache
    gService = std::make_unique<BlockingService>(cfg);
  }
  return *gService;
}

struct Handle {
  std::shared_ptr<TranslationModel> model;
};

std::string jstr(JNIEnv *env, jstring s) {
  if (s == nullptr) return std::string();
  const char *c = env->GetStringUTFChars(s, nullptr);
  std::string out(c ? c : "");
  if (c) env->ReleaseStringUTFChars(s, c);
  return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_splitreader_data_bergamot_BergamotNative_load(JNIEnv *env, jobject,
                                                                                      jstring configYaml) {
  std::lock_guard<std::mutex> lock(gMutex);
  try {
    // validate=false: the config is a decoder config, not a full marian training config.
    auto options = parseOptionsFromString(jstr(env, configYaml), /*validate=*/false);
    // Empty MemoryBundle => parameters, vocabs and shortlist are read from the paths in the config.
    auto *h = new Handle{std::make_shared<TranslationModel>(options, MemoryBundle{}, /*replicas=*/1)};
    return reinterpret_cast<jlong>(h);
  } catch (const std::exception &e) {
    gLastError = e.what();
    return 0;
  } catch (...) {
    gLastError = "unknown native error in load";
    return 0;
  }
}

JNIEXPORT jstring JNICALL Java_com_example_splitreader_data_bergamot_BergamotNative_translate(JNIEnv *env, jobject,
                                                                                             jlong handle,
                                                                                             jstring text) {
  std::lock_guard<std::mutex> lock(gMutex);
  try {
    auto *h = reinterpret_cast<Handle *>(handle);
    if (h == nullptr) {
      gLastError = "translate on null handle";
      return nullptr;
    }
    ResponseOptions opts;
    opts.HTML = false;
    // translateMultiple takes one ResponseOptions per input string.
    std::vector<std::string> inputs{jstr(env, text)};
    std::vector<ResponseOptions> perInput{opts};
    auto responses = service().translateMultiple(h->model, std::move(inputs), perInput);
    if (responses.empty()) {
      gLastError = "translate returned no response";
      return nullptr;
    }
    return env->NewStringUTF(responses.front().target.text.c_str());
  } catch (const std::exception &e) {
    gLastError = e.what();
    return nullptr;
  } catch (...) {
    gLastError = "unknown native error in translate";
    return nullptr;
  }
}

JNIEXPORT void JNICALL Java_com_example_splitreader_data_bergamot_BergamotNative_unload(JNIEnv *, jobject,
                                                                                       jlong handle) {
  std::lock_guard<std::mutex> lock(gMutex);
  try {
    delete reinterpret_cast<Handle *>(handle);
  } catch (const std::exception &e) {
    gLastError = e.what();
  } catch (...) {
    gLastError = "unknown native error in unload";
  }
}

JNIEXPORT jstring JNICALL Java_com_example_splitreader_data_bergamot_BergamotNative_lastError(JNIEnv *env, jobject) {
  std::lock_guard<std::mutex> lock(gMutex);
  return env->NewStringUTF(gLastError.c_str());
}

}  // extern "C"
