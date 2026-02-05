add_library(backtest STATIC
  src/placeholder.cpp
)

target_include_directories(backtest PUBLIC include)
target_link_libraries(backtest PUBLIC common)
