# Java Shell

A small shell implementation written in Java for the Codecrafters "Build Your Own Shell" challenge.

The goal of the project is to mimic a subset of POSIX shell behavior closely enough to run the challenge test suite. The shell reads commands from standard input, parses them, executes builtins or external programs, and supports job control and pipelines.

## Features

- Builtins: `exit`, `echo`, `type`, `pwd`, `cd`, `jobs`
- Background execution with `&`
- Automatic reaping of completed background jobs before each prompt
- `jobs` output with `Running` and `Done` states
- Job number recycling when finished jobs are removed
- Pipelines with two or more stages
- Pipelines that mix builtins and external commands
- Command parsing with quoting and basic redirection support

## Examples

```sh
$ echo hello
hello

$ pwd
/Users/you/code/codecrafters-shell-java

$ sleep 10 &
[1] 84470

$ jobs
[1]+  Running                 sleep 10 &

$ echo orange | wc
	1       1      7

$ ls | type exit
exit is a shell builtin
```

## Running Locally

```sh
./your_program.sh
```

If you want to compile the Java source directly:

```sh
mvn -q -DskipTests compile
```

## Project Structure

- `src/main/java/Main.java` - shell implementation and command dispatch
- `your_program.sh` - launch script used by Codecrafters
- `pom.xml` - Maven build configuration

## Development Notes

- The shell is implemented as a single interactive loop in `Main.java`.
- Background jobs are stored in memory and reaped both before each prompt and when `jobs` is executed.
- Pipelines are handled stage-by-stage, with builtins executed in-process when they appear anywhere in the chain.

## Publishing to GitHub

To publish this repository to GitHub:

1. Create a new empty repository on GitHub.
2. Add it as a remote:

```sh
git remote add origin https://github.com/<your-username>/<your-repo>.git
```

3. Push the current branch:

```sh
git push -u origin master
```

If your default branch is `main`, push that branch instead.

## Notes

This repository was built stage-by-stage for Codecrafters, so the implementation focuses on the behaviors required by the challenge tests rather than full shell compatibility.
