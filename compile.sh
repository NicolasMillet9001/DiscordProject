#!/bin/bash

# Configuration
SOURCE_DIR="src/main/java"
OUTPUT_DIR="bin"

# Créer le dossier bin s'il n'existe pas
mkdir -p "$OUTPUT_DIR"

# Compiler tous les fichiers .java
echo "Compilation en cours..."
find "$SOURCE_DIR" -name "*.java" > sources.txt
javac -d "$OUTPUT_DIR" -sourcepath "$SOURCE_DIR" @sources.txt
rm sources.txt

if [ $? -eq 0 ]; then
    echo "✅ Compilation terminée avec succès dans le dossier '$OUTPUT_DIR'."
else
    echo "❌ Erreur lors de la compilation."
    exit 1
fi
