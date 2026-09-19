cmake_minimum_required(VERSION 3.22)
project(videoeditor_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)

find_library(LOG_LIB log)
include(FetchContent)

# ggml (CPU-Backend) aus dem whisper.cpp-Baum
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(ggml)

add_library(whisperjni SHARED
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)
target_compile_definitions(whisperjni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisperjni PRIVATE WHISPER_VERSION="1.9.4")
target_include_directories(whisperjni PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisperjni PRIVATE -march=armv8.2-a+fp16)
    target_compile_options(ggml PRIVATE -march=armv8.2-a+fp16)
endif()
target_compile_options(whisperjni PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections)
target_link_options(whisperjni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(whisperjni ${LOG_LIB} android ggml)
