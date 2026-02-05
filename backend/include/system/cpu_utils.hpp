#pragma once

#ifdef _WIN32
#include <windows.h>
#else
#include <sched.h>
#include <pthread.h>
#endif

#include <thread>
#include <iostream>

namespace argentum::system {

/**
 * @brief Pin the current thread to a specific CPU core.
 * Essential for HFT to avoid cache trashing by OS scheduler.
 */
inline void pin_thread_to_core(int core_id) {
#ifdef _WIN32
    HANDLE thread = GetCurrentThread();
    DWORD_PTR mask = (1ULL << core_id);
    if (SetThreadAffinityMask(thread, mask) == 0) {
        std::cerr << "[System] Failed to pin thread to core " << core_id << std::endl;
    }
#else
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);
    pthread_t current_thread = pthread_self();
    if (pthread_setaffinity_np(current_thread, sizeof(cpu_set_t), &cpuset) != 0) {
        std::cerr << "[System] Failed to pin thread to core " << core_id << std::endl;
    }
#endif
}

} // namespace argentum::system
