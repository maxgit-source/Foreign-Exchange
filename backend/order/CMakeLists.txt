add_library(order STATIC
  src/placeholder.cpp
)

target_include_directories(order PUBLIC include)
target_link_libraries(order PUBLIC common)
