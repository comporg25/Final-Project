42 .
-5 .
+7 .

5 dup . .
5 6 drop .
5 6 swap . .
5 6 over . . .

7 3 + .
7 3 - .
7 3 * .
7 3 / .
7 3 mod .

: sq dup * ;
: cube dup sq * ;

6 sq .
3 cube .

1 if 111 . else 222 . then
0 if 111 . else 222 . then

5
begin
dup .
1 -
dup
until

5
begin
dup .
1 -
dup
while
repeat

