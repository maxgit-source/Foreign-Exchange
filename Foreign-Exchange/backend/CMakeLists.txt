# Define source groups
set(CORE_SOURCES src/core/dummy.c src/core/time_utils.cpp)
set(NETWORK_SOURCES src/network/socket_manager.c)
set(DATAFEED_SOURCES src/datafeed/market_parser.c src/datafeed/normalizer.c src/datafeed/feed_player.cpp)
set(BUS_SOURCES src/bus/message_bus.cpp src/bus/message_protocol.cpp)
set(ENGINE_SOURCES src/engine/order_book.cpp)
set(RISK_SOURCES src/risk/risk_manager.cpp)
set(TRADING_SOURCES src/trading/order_manager.cpp)
set(BACKTEST_SOURCES src/backtest/backtest_engine.cpp)
set(PERSIST_SOURCES src/persist/data_writer.cpp)
set(CODEC_SOURCES src/codec/market_tick_codec.cpp)
# New modules are header-only for now, but added to includes

if(ARGENTUM_USE_FLATBUFFERS)
    find_package(Flatbuffers REQUIRED)
    find_program(FLATC_EXECUTABLE flatc REQUIRED)

    set(FB_SCHEMA ${CMAKE_CURRENT_SOURCE_DIR}/schema/argentum.fbs)
    set(FB_GENERATED_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated/flatbuffers)
    file(MAKE_DIRECTORY ${FB_GENERATED_DIR})

    add_custom_command(
        OUTPUT ${FB_GENERATED_DIR}/argentum_generated.h
        COMMAND ${FLATC_EXECUTABLE} --cpp --scoped-enums -o ${FB_GENERATED_DIR} ${FB_SCHEMA}
        DEPENDS ${FB_SCHEMA}
        COMMENT "Generating FlatBuffers schema"
    )

    add_custom_target(argentum_flatbuffers_gen DEPENDS ${FB_GENERATED_DIR}/argentum_generated.h)

    add_library(argentum_flatbuffers_generated INTERFACE)
    add_dependencies(argentum_flatbuffers_generated argentum_flatbuffers_gen)
    target_include_directories(argentum_flatbuffers_generated INTERFACE ${FB_GENERATED_DIR})
endif()

# Libraries
add_library(argentum_core STATIC ${CORE_SOURCES})
target_include_directories(argentum_core PUBLIC include)

add_library(argentum_bus STATIC ${BUS_SOURCES})
target_include_directories(argentum_bus PUBLIC include)
target_link_libraries(argentum_bus PUBLIC argentum_core)

add_library(argentum_codec STATIC ${CODEC_SOURCES})
target_include_directories(argentum_codec PUBLIC include)
target_link_libraries(argentum_codec PUBLIC argentum_bus argentum_core)

if(ARGENTUM_USE_FLATBUFFERS)
    target_compile_definitions(argentum_codec PUBLIC ARGENTUM_USE_FLATBUFFERS)
    target_link_libraries(argentum_codec PUBLIC Flatbuffers::flatbuffers argentum_flatbuffers_generated)
endif()

add_library(argentum_network STATIC ${NETWORK_SOURCES})
target_include_directories(argentum_network PUBLIC include)
target_link_libraries(argentum_network PUBLIC argentum_core)

add_library(argentum_datafeed STATIC ${DATAFEED_SOURCES})
target_include_directories(argentum_datafeed PUBLIC include)
target_link_libraries(argentum_datafeed PUBLIC argentum_core argentum_codec)

add_library(argentum_engine STATIC ${ENGINE_SOURCES})
target_include_directories(argentum_engine PUBLIC include)
target_link_libraries(argentum_engine PUBLIC argentum_core)

add_library(argentum_risk STATIC ${RISK_SOURCES})
target_include_directories(argentum_risk PUBLIC include)
target_link_libraries(argentum_risk PUBLIC argentum_core)

add_library(argentum_trading STATIC ${TRADING_SOURCES})
target_include_directories(argentum_trading PUBLIC include)
target_link_libraries(argentum_trading PUBLIC argentum_risk argentum_engine argentum_core)

add_library(argentum_backtest STATIC ${BACKTEST_SOURCES})
target_include_directories(argentum_backtest PUBLIC include)
target_link_libraries(argentum_backtest PUBLIC argentum_core)

add_library(argentum_persist STATIC ${PERSIST_SOURCES})
target_include_directories(argentum_persist PUBLIC include)
target_link_libraries(argentum_persist PUBLIC argentum_core)

add_subdirectory(api)

# Executable
add_executable(argentum_node src/main.cpp)
target_link_libraries(argentum_node PRIVATE 
    argentum_trading 
    argentum_risk 
    argentum_engine 
    argentum_network 
    argentum_datafeed
    argentum_bus
    argentum_codec
    argentum_persist
    argentum_backtest
    argentum_api
    argentum_core
)

add_executable(argentum_pipeline_benchmark src/benchmark/pipeline_benchmark.cpp)
target_link_libraries(argentum_pipeline_benchmark PRIVATE
    argentum_bus
    argentum_codec
    argentum_persist
    argentum_core
)

add_subdirectory(tests)
