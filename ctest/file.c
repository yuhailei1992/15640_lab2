#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

char *msg = "hello darkness my old friend, i came here to talk with u";

int main()
{
    int filedesc = open("file.txt", O_CREAT | O_RDWR, 244);

    // write to file
    write(filedesc, msg, strlen(msg));
    close(filedesc);

    int fd = open("file.txt", O_RDWR, 244);

    // read from file and show
    char buf[200];
    read(fd, buf, 200);
    printf("the msg read is %s\n", buf);
    // unlink the file
    //close(filedesc);
    close(fd);
    unlink("file.txt");
    int fd1 = open("./../doc/", O_RDONLY);
    int fd2 = open("./../lib/", O_RDWR, 244);
    return 0;
}
