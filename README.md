This is very normal command line minecraft game.

## prerequisites
- install jvm(11+)
- clone this repo

```shell
# get sdk(Java11+) somehow. 
$ sdk use java 11.0.9-amzn

# clone this repo
$ git clone git@github.com:vsanna/cmdline_mine_sweeper.git
```

## how to start game
```shell
$ ./gradlew shadowJar
$ java -jar build/libs/cmdline_mine_sweeper-1.0-SNAPSHOT-all.jar

# To see helps
$ java -jar build/libs/cmdline_mine_sweeper-1.0-SNAPSHOT-all.jar --help
```

## how to play the game
```shell
> help

# Help
## how to win
- when you "flag" all the cells that have mine, you win.
- if you "open" any cell that has mine before you win, the cell explodes and you lose.

## what you can do
You can choose one command to run from below every turn.

- open, open posX posY
    - open a cell which has not opened yet. If the cell has a mine in it, it will explode and the game will end as faied
    - when you don't specify position of the cell to open, you will be asked in the subsequent prompt
- flag, flag posX posY
    - flag/un-flag a cell which has not opened yet.
    - when you don't specify position of the cell to open, you will be asked in the subsequent prompt
- showMap
    - show current map
- giveUp
    - exit the game
- help
    - show this help

Enjoy!
```