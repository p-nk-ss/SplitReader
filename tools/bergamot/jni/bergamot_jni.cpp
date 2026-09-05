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

// JNI's "UTF" is *modified* UTF-8: astral codepoints appear as CESU-8 surrogate pairs and U+0000
// as two bytes. GetStringUTFChars would therefore hand marian invalid UTF-8, and passing marian's
// real UTF-8 back to NewStringUTF aborts the process under CheckJNI. So both directions convert
// through UTF-16 explicitly. Malformed input becomes U+FFFD rather than an error — translating a
// string must never be able to kill the app.
constexpr char32_t kReplacement = 0xFFFD;

void appendUtf8(std::string &out, char32_t cp) {
  if (cp < 0x80) {
    out.push_back(static_cast<char>(cp));
  } else if (cp < 0x800) {
    out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  } else if (cp < 0x10000) {
    out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  } else {
    out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
  }
}

/// Reads a Java string as standard UTF-8. Unpaired surrogates become U+FFFD.
std::string toUtf8(JNIEnv *env, jstring s) {
  if (s == nullptr) return std::string();
  const jsize len = env->GetStringLength(s);
  const jchar *units = env->GetStringChars(s, nullptr);
  if (units == nullptr) return std::string();
  std::string out;
  out.reserve(static_cast<size_t>(len) + static_cast<size_t>(len) / 2);
  for (jsize i = 0; i < len; ++i) {
    char32_t cp = units[i];
    if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len && units[i + 1] >= 0xDC00 && units[i + 1] <= 0xDFFF) {
      cp = 0x10000 + ((cp - 0xD800) << 10) + (units[++i] - 0xDC00);
    } else if (cp >= 0xD800 && cp <= 0xDFFF) {
      cp = kReplacement;  // unpaired surrogate
    }
    appendUtf8(out, cp);
  }
  env->ReleaseStringChars(s, units);
  return out;
}

/// Builds a Java string from standard UTF-8. Malformed sequences become U+FFFD.
jstring fromUtf8(JNIEnv *env, const std::string &s) {
  std::vector<jchar> units;
  units.reserve(s.size());
  const auto *p = reinterpret_cast<const unsigned char *>(s.data());
  const size_t n = s.size();
  for (size_t i = 0; i < n;) {
    const unsigned char b = p[i];
    char32_t cp;
    size_t extra;
    if (b < 0x80) {
      cp = b;
      extra = 0;
    } else if ((b & 0xE0) == 0xC0) {
      cp = b & 0x1F;
      extra = 1;
    } else if ((b & 0xF0) == 0xE0) {
      cp = b & 0x0F;
      extra = 2;
    } else if ((b & 0xF8) == 0xF0) {
      cp = b & 0x07;
      extra = 3;
    } else {
      cp = kReplacement;  // stray continuation byte, or a 5+ byte lead
      extra = 0;
    }
    size_t consumed = 1;
    for (size_t k = 1; k <= extra; ++k) {
      if (i + k >= n || (p[i + k] & 0xC0) != 0x80) {
        cp = kReplacement;  // truncated, or a bad continuation byte
        break;
      }
      cp = (cp << 6) | (p[i + k] & 0x3F);
      ++consumed;
    }
    i += consumed;
    // Surrogates and anything past U+10FFFF are not legal UTF-8.
    if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) cp = kReplacement;
    if (cp < 0x10000) {
      units.push_back(static_cast<jchar>(cp));
    } else {
      cp -= 0x10000;
      units.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
      units.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
    }
  }
  return env->NewString(units.data(), static_cast<jsize>(units.size()));
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_example_splitreader_data_bergamot_BergamotNative_load(JNIEnv *env, jobject,
                                                                                      jstring configYaml) {
  std::lock_guard<std::mutex> lock(gMutex);
  try {
    // validate=false: the config is a decoder config, not a full marian training config.
    auto options = parseOptionsFromString(toUtf8(env, configYaml), /*validate=*/false);
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
    // Convert the argument before validating the handle, so the UTF-16 -> UTF-8 path runs on
    // every call rather than only once a model has been loaded.
    std::vector<std::string> inputs{toUtf8(env, text)};
    auto *h = reinterpret_cast<Handle *>(handle);
    if (h == nullptr) {
      gLastError = "translate on null handle";
      return nullptr;
    }
    ResponseOptions opts;
    opts.HTML = false;
    // translateMultiple takes one ResponseOptions per input string.
    std::vector<ResponseOptions> perInput{opts};
    auto responses = service().translateMultiple(h->model, std::move(inputs), perInput);
    if (responses.empty()) {
      gLastError = "translate returned no response";
      return nullptr;
    }
    return fromUtf8(env, responses.front().target.text);
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
  return fromUtf8(env, gLastError);
}

}  // extern "C"
