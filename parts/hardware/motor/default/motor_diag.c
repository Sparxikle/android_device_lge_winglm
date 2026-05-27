#include <stdio.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <poll.h>

#define MOTOR_UP          0xDC01
#define MOTOR_DOWN        0xDC02
#define MOTOR_STOP        0xDC05
#define MOTOR_GET_MAX_PPS 0xDC06
#define DEVICE_NODE       "/dev/stdrv"

void run_command(int fd, int cmd, const char* name) {
    printf("Sending %s (0x%X)...\n", name, cmd);
    int ret = ioctl(fd, cmd, 0);
    if (ret < 0) {
        printf("FAILED: %s (%s)\n", name, strerror(errno));
        return;
    }
    printf("Command sent. Waiting for completion (poll)...\n");

    struct pollfd pfd = {.fd = fd, .events = POLLIN};
    int poll_ret = poll(&pfd, 1, 5000);
    if (poll_ret > 0) {
        printf("SUCCESS: Motor signaled completion.\n");
        char buf[16];
        read(fd, buf, sizeof(buf)); // Clear interrupt
    } else if (poll_ret == 0) {
        printf("TIMEOUT: Motor did not signal completion within 5s.\n");
    } else {
        printf("POLL ERROR: %s\n", strerror(errno));
    }
}

int main(int argc, char** argv) {
    if (argc < 2) {
        printf("Usage: %s <up|down|handshake|stop>\n", argv[0]);
        return 1;
    }

    int fd = open(DEVICE_NODE, O_RDWR);
    if (fd < 0) {
        printf("ERROR: Could not open %s (%s)\n", DEVICE_NODE, strerror(errno));
        return 1;
    }

    if (strcmp(argv[1], "handshake") == 0) {
        int max_pps = 0;
        if (ioctl(fd, MOTOR_GET_MAX_PPS, &max_pps) < 0) {
            printf("Handshake FAILED: %s\n", strerror(errno));
        } else {
            printf("Handshake SUCCESS: max_pps = %d\n", max_pps);
        }
    } else if (strcmp(argv[1], "up") == 0) {
        run_command(fd, MOTOR_UP, "MOVE_UP");
    } else if (strcmp(argv[1], "down") == 0) {
        run_command(fd, MOTOR_DOWN, "MOVE_DOWN");
    } else if (strcmp(argv[1], "stop") == 0) {
        run_command(fd, MOTOR_STOP, "STOP");
    } else {
        printf("Unknown command: %s\n", argv[1]);
    }

    close(fd);
    return 0;
}
