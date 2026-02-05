add_library(api STATIC
  src/placeholder.cpp
)

target_include_directories(api PUBLIC include)
target_link_libraries(api PUBLIC common)
