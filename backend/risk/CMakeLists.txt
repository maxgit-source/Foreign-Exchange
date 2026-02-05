add_library(risk STATIC
  src/placeholder.cpp
)

target_include_directories(risk PUBLIC include)
target_link_libraries(risk PUBLIC common)
