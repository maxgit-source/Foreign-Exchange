add_library(analysis STATIC
  src/placeholder.cpp
)

target_include_directories(analysis PUBLIC include)
target_link_libraries(analysis PUBLIC common)
