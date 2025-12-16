# Final Project

This project implements a Tiny Forth compiler in **Java**.
The compiler translates a small subset of the Forth language into x86-64
assembly and builds a native executable.

The project is designed to run on the **the.hell server**.
All build, run, and clean steps are automated using a provided `Makefile`.

---

## Requirements

The following tools must be available (all are installed on the.hell):

- Java JDK (`javac`, `java`)
- GNU assembler and linker
- GCC

---

## Project Files

- `ForthCompiler.java` – Java implementation of the compiler
- `Makefile` – build, run, and clean instructions
- `code.fs` – input Forth program
- `README.md` – project description and usage instructions

---

## How to Build

From the project directory, run:

```bash
make

## How to Run

## After building, run:

./code

## How to Clean

To remove all generated files, run:

make clean
