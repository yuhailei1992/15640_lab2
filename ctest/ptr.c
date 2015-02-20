#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#define LEN 100

void main () {
    int k = 0;
    scanf("%d", &k);
    printf("k is %d\n", k);
    char** strarray = (char **)malloc(LEN * k);

    int i = 0;
    for (; i < k; i++) {
        char tmp[LEN];
        memset(tmp, 0, LEN);
        sprintf(tmp, "%d", i);
        printf("%s\n", tmp);
        // copy into results
        memcpy((void *)(strarray + LEN * i), tmp, LEN);
    }

    int j = 0;
    for ( ; j < k; j++) {
        printf("%s\n", (char *)(strarray + LEN * j));
    }

    char **index = (char **)malloc(k * sizeof(char *));
    i = 0;
    for (; i < k; i++) {
        char *tmp1 = (char *)malloc(LEN);
        memset(tmp1, 0, LEN);
        sprintf(tmp1, "%d", (i+10));
        index[i] = tmp1;
    }

    for (j = 0; j < k; j++) {
        printf("%s\n", index[j]);
    }
}
