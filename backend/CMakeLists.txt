# Define source groups
set(CORE_SOURCES src/core/dummy.c)
set(NETWORK_SOURCES src/network/socket_manager.c)
set(DATAFEED_SOURCES src/datafeed/market_parser.c src/datafeed/normalizer.c src/datafeed/feed_player.cpp)
set(BUS_SOURCES src/bus/message_bus.cpp src/bus/message_protocol.cpp)
set(ENGINE_SOURCES src/engine/order_book.cpp)
set(RISK_SOURCES src/risk/risk_manager.cpp)
set(TRADING_SOURCES src/trading/order_manager.cpp)
set(BACKTEST_SOURCES src/backtest/backtest_engine.cpp)
set(PERSIST_SOURCES src/persist/data_writer.cpp)
# New modules are header-only for now, but added to includes

# Libraries
add_library(argentum_core STATIC ${CORE_SOURCES})
target_include_directories(argentum_core PUBLIC include)

add_library(argentum_network STATIC ${NETWORK_SOURCES})
target_include_directories(argentum_network PUBLIC include)
target_link_libraries(argentum_network PUBLIC argentum_core)

add_library(argentum_datafeed STATIC ${DATAFEED_SOURCES})
target_include_directories(argentum_datafeed PUBLIC include)
target_link_libraries(argentum_datafeed PUBLIC argentum_core)

add_library(argentum_bus STATIC ${BUS_SOURCES})
target_include_directories(argentum_bus PUBLIC include)
target_link_libraries(argentum_bus PUBLIC argentum_core)

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

# Executable
add_executable(argentum_node src/main.cpp)
target_link_libraries(argentum_node PRIVATE 
    argentum_trading 
    argentum_risk 
    argentum_engine 
    argentum_network 
    argentum_datafeed
    argentum_bus
    argentum_persist
    argentum_backtest
    argentum_core
)
