# Start Client A in background
(
  echo "ClientA"
  sleep 2
  echo "/join room1"
  sleep 1
  echo "MessageToRoom1"
  sleep 1
  echo "/msg ClientB ThisIsPrivate"
  sleep 5
  echo "bye"
) | java -cp bin fr.unilasalle.chat.client.Client localhost 5001 > client_a_out.txt &
PID_A=$!

# Start Client B in background
sleep 1
(
  echo "ClientB"
  sleep 5
  # Client B stays in general, should NOT see "MessageToRoom1"
  # Client B SHOULD see "ThisIsPrivate"
  echo "bye"
) | java -cp bin fr.unilasalle.chat.client.Client localhost 5001 > client_b_out.txt &
PID_B=$!

wait $PID_A
wait $PID_B

echo "--- Client A Output ---"
cat client_a_out.txt
echo "\n--- Client B Output ---"
cat client_b_out.txt
