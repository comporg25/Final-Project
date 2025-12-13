# Tiny Forth Compiler

This project is a **toy compiler** for a very small subset of the Forth language.

It is intentionally minimal and educational: it shows how to go from a simple
stack-based language (Forth-style) to **x86-64 assembly**

---

## Supported Forth Subset

Right now the compiler only supports:

- **Integer literals** (e.g. `2`, `42`, `-5`)
- `dup` – duplicate the top of the stack  
  - Stack: `… a` → `… a a`
- `*` – multiply the top two stack values  
  - Stack: `… a b` → `… (a * b)`
- `.` – print the top of the stack (and pop it)  
  - Stack: `… a` → prints `a`, stack becomes `…`

Anything else (other words, definitions, control flow, etc.) is **not supported** yet.

---

## How It Works (High-Level)

1. **Input**: a `.fs` file containing a sequence of tokens separated by whitespace.  
   Example:

   ```bash
   28 3 dup * .

2. **Tokenization**:

   * The compiler opens the source file and reads it **byte by byte**.
   * It builds tokens by grouping non-whitespace characters.
   * Whitespace (`' '`, `'\n'`, `'\t'`, etc.) separates tokens.

3. **Parsing / Code Generation**:
   For each token:

   * If it’s a number (e.g. `42`), generate assembly to `push` that value on the stack.
   * If it’s `dup`, generate assembly that:

     * pops the top value into a register,
     * pushes it twice (duplicating it).
   * If it’s `*`, generate assembly that:

     * pops the top two values,
     * multiplies them,
     * pushes the result.
   * If it’s `.`, generate assembly that:

     * pops the top value,
     * passes it to `printf` with the format string `"%d\n"`,
     * prints it.

4. **Output**:

   * The compiler builds up a big `asmCode` string containing:

     * `.rodata` section with the `"%d\n"` format string.
     * `.text` section with `_start:` and the generated instructions.
     * An `exit(0)` syscall at the end.
   * This assembly is written to `<input_basename>.s` next to the source file.

5. **Compilation**:

   * You then use `gcc` (or another assembler/linker) to build a native binary.

---

## Example

### Example Forth Program

`code.fs`:

```forth
28 3 dup * .
```

**Stack behavior**:

* `28` → `28`
* `3` → `28 3`
* `dup` → `28 3 3`
* `*` → `28 9`
* `.` → prints `9`

Expected output:

```text
9
````

### Running the Compiler

#### With ts-node (dev-friendly)

```bash
npx ts-node src/forth_compiler.ts code.fs
# This generates code.s
gcc code.s -no-pie -o code
./code
````

You should see:

```text
9
````

## Implementation Notes

* The compiler uses **Node’s `fs.readSync`** to read the source file **byte by byte**.
* Whitespace is detected with a simple character check (e.g. space, newline, tab).
* Assembly uses:

  * `push` / `pop` to simulate a Forth-style stack.
  * `imul` for multiplication.
  * `printf` from the C standard library for printing (with `"%d\n"`).
    
    ```asm
    pop %rsi
    mov $fmt, %rdi
    xor %rax, %rax
    call printf
    ````
  * A final Linux syscall `exit(0)`:

    ```asm
    mov $60, %rax    # syscall: exit
    mov $0, %rdi     # status = 0
    syscall
    ````

## Solution
## How to Run

From the project directory, execute the following commands:

	```bash
	make
	./code
### Prerequisites

- Linux x86-64 (or WSL)
- `as` and `ld` from GNU binutils (usually installed with `build-essential`)
- Java (JDK) installed (`javac` and `java` available in your PATH)

### 1. Compile the Java compiler

From the folder containing `ForthCompiler.java`:

```bash
javac ForthCompiler.java
````
Then run the below command, providing the path for the `code.fs` forth file:

```bash
java ForthCompiler ./code.fs
````

After running the program you should get the executable `code` file.
Then run that as usual:

```bash
./code
````

You should get the answer.

