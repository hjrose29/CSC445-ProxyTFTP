WINDOWS=(1 8 64)
DROPS=(0.0 0.01)
OUTPUT="results.csv"

echo "window,drop,trial,time_ms,bytes,Bps" > $OUTPUT

for win in "${WINDOWS[@]}"; do
  for drop in "${DROPS[@]}"; do
    echo "Testing configuration: window=$win, drop=$drop"
    echo "Please run: java ProxyServer --drop=$drop --window=$win"
    read -p "Press enter once proxy is running..."

    for trial in {1..15}; do
      echo "Trial $trial/15..."

      START=$(python3 -c "import time; print(int(time.time() * 1000))")
      echo "$START"
      java Client.java > tmp_output.txt
      END=$(python3 -c "import time; print(int(time.time() * 1000))")
      echo "$END"
      TIME=$((END - START))
      BYTES=144003
      BPS=$((BYTES / TIME))

      echo "$win,$drop,$trial,$TIME,$BYTES,$BPS" >> $OUTPUT
      echo "$win $drop trial $trial → $TIME ms, $BPS B/s"
    done

    echo "Done with window=$win, drop=$drop"
    echo ""
  done
done

