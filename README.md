## Terraflare

### Exporting DNS Records

1. Install Clojure.

    ```sh
    brew install clojure
    ```

2. Setup CloudFlare ID with secret.

    ```sh
    export CLOUDFLARE_EMAIL={{ YOUR-CF-ID }}
    export CLOUDFLARE_TOKEN={{ YOUR-CF-TOKEN }}
    ```

3. Export json for a domain. e.g.,

    ```sh
    clojure -m terraflare.record ridi.com
    ```

4. Go [hcl2json](https://www.hcl2json.com/) and get `.hcl` from the output.

5. GOTO 3 until you don't need more.
