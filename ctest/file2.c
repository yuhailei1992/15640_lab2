#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

char *msg = "adfaldksfajhello darkness my old friend, i came here to talk with u";

int main()
{
    int filedesc = open("upload.txt", O_RDWR);

    // write to file
    write(filedesc, msg, strlen(msg));
    close(filedesc);

    int fd = open("test.txt", O_RDONLY);
    char buf[100];
    read(fd, buf, 100);
    close(fd);
    return 0;
}
