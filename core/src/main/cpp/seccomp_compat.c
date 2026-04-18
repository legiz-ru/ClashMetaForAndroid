/*
 * seccomp_compat.c — ARM 32-bit seccomp compatibility shim.
 *
 * Go 1.20+ uses time64 variants of Linux syscalls on 32-bit ARM to avoid
 * the year-2038 problem (e.g. sched_rr_get_interval_time64 = syscall 422,
 * futex_time64 = 421).  Android 9 (API 28) seccomp allowlists were frozen
 * before those syscalls were added to the ARM EABI table (kernel 5.1+), so
 * the kernel sends SIGSYS to the process, killing it immediately.
 *
 * Fix: install a SIGSYS signal handler via __attribute__((constructor)) so
 * it is registered *before* the Go runtime initialises.  The handler detects
 * seccomp kills (si_code == SYS_SECCOMP) and replaces the syscall return
 * value with -ENOSYS in the saved register context.  When the signal handler
 * returns, execution continues at the instruction after the SVC (the kernel
 * sets arm_pc to the return address before delivering the signal), and Go
 * sees ENOSYS — which it treats as "not supported" and falls back to the
 * non-time64 variant of the syscall.
 *
 * Go does not install its own SIGSYS handler, so this handler remains active
 * for the lifetime of the process.
 *
 * Only compiled on ARM 32-bit; no-ops on all other architectures.
 */

#if defined(__arm__) && !defined(__aarch64__)

#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <sys/ucontext.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

static void seccomp_sigsys_handler(int signo, siginfo_t *info, void *context) {
    (void)signo;
    if (info == NULL || info->si_code != SYS_SECCOMP) {
        return;
    }
    ucontext_t *uc = (ucontext_t *)context;
    if (uc == NULL) {
        return;
    }
    /*
     * Return -ENOSYS in r0.  Go's ARM syscall wrappers check whether r0 is
     * in the error range (> 0xfffff000) and propagate the errno, so the
     * runtime probe function will see ENOSYS and skip the time64 syscall.
     */
    uc->uc_mcontext.arm_r0 = (uint32_t)(-ENOSYS);
}

__attribute__((constructor))
static void install_seccomp_sigsys_compat(void) {
    struct sigaction sa;
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = seccomp_sigsys_handler;
    sa.sa_flags = SA_SIGINFO;
    sigaction(SIGSYS, &sa, NULL);
}

#endif /* defined(__arm__) && !defined(__aarch64__) */
