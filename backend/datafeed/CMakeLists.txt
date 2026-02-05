add_library(datafeed STATIC
  src/placeholder.c
)

target_include_directories(datafeed PUBLIC include)
