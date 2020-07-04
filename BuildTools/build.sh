#!/bin/sh
echo "Checking BuildTools cache"

#Local Maven Repo
MAVEN_DIR="$HOME/.m2"

#CraftBukkit in local repo
CRAFTBUKKIT="${MAVEN_DIR}/repository/org/bukkit/craftbukkit"

#Versions
array=("1.16.1" "1.15.2" "1.14.4")

#Download BuildTools jar
curl -s -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

for i in "${array[@]}"
do
  VERSION_DIR="${CRAFTBUKKIT}/${i}-R0.1-SNAPSHOT"

    if [ -d "$VERSION_DIR" ]; then
      echo "CraftBukkit version ${i} is cached!"
    else
      echo "CraftBukkit version ${i} isn't cached!"
      echo "Running BuildTools!"
      java -jar BuildTools.jar --rev ${i} --compile craftbukkit | awk 'NR <= 3'
      echo "Compiled CraftBukkit ${i}"
    fi
done
