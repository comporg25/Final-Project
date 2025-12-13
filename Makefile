JAVAC = javac
JAVA  = java

SRC = ForthCompiler.java
FS ?= code.fs
OUT = code

all: $(OUT)

$(OUT): $(SRC) $(FS)
	$(JAVAC) $(SRC)
	$(JAVA) ForthCompiler $(FS)

clean:
	rm -f *.class *.o *.s $(OUT)

