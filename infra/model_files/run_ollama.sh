#!/bin/sh
set -e

# Start Ollama in the background
ollama serve &

echo "Waiting for Ollama to spin up..."

# Use 'ollama list' as a health check (faster and no dependencies)
until ollama list > /dev/null 2>&1; do
  sleep 0.2
done

# Check if model exists before pulling (Massive speed save on restarts)
if ! ollama list | grep -q "qwen2.5:1.5b"; then
  echo "Pulling qwen2.5:1.5b..."
  ollama pull qwen2.5:1.5b
else
  echo "qwen2.5:1.5b is already here. Ready to go!"
fi

# Keep the container alive by waiting on the serve process
wait
