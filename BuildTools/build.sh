#!/bin/sh
echo "Checking BuildTools cache"

curl -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

#Local Maven Repo
MAVEN_DIR="$HOME/.m2"

#CraftBukkit in local repo
CRAFTBUKKIT="${MAVEN_DIR}/repository/org/bukkit/craftbukkit"

#Versions
#VERSION_1_16="1.16.1"
VERSION_1_15="1.15.2"
#VERSION_1_14="1.14.4"

#array=("${VERSION_1_16}" "${VERSION_1_15}" "${VERSION_1_14}")
array=("${VERSION_1_15}")

for i in "${array[@]}"
do
  VERSION_DIR="${CRAFTBUKKIT}/${i}-R0.1-SNAPSHOT"

    if [ -d "$VERSION_DIR" ]; then
      echo "${i} is cached!"
    else
      echo "${i} isn't cached!"
      echo "Running BuildTools!"
      java -jar BuildTools.jar --rev ${i} --compile craftbukkit > /dev/null 2>&1
      echo "Compiled CraftBukkit ${i}"
    fi
done
