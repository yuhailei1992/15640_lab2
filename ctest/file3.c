#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

char *msg = "hello darkness my old friend, i came here to talk with u";

int main()
{
    int filedesc = open("../servercache/test2.txt", O_RDWR);

    // write to file
    write(filedesc, msg, strlen(msg));
    close(filedesc);

    char buf[100];
    read(filedesc, buf, 100);
    return 0;
}
