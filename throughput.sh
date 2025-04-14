HOST="localhost"  # IP of the proxy
WINDOWS=(1 8 64)
DROPS=(0.0 0.01)
OUTPUT="results.csv"

echo "window,drop,trial,time_ms,bytes,kbps" > $OUTPUT

for win in "${WINDOWS[@]}"; do
  for drop in "${DROPS[@]}"; do
    echo "Testing configuration: window=$win, drop=$drop"
    echo "Please run: java ProxyServer --drop=$drop --window=$win"
    read -p "Press enter once proxy is running..."

    for trial in {1..30}; do
      echo "Trial $trial/30..."

      START=$(date +%s%3N)
      java Client $HOST > tmp_output.txt
      END=$(date +%s%3N)

      TIME=$((END - START))
      BYTES=$(grep '\[RESULT\]' tmp_output.txt | sed 's/.*fileSize=\([0-9]*\).*/\1/')
      KBPS=$((BYTES * 1000 / 1024 / TIME))

      echo "$win,$drop,$trial,$TIME,$BYTES,$KBPS" >> $OUTPUT
      echo "$win $drop trial $trial → $TIME ms, $KBPS KB/s"
    done

    echo "Done with window=$win, drop=$drop"
    echo ""
  done
done

