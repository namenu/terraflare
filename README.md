> This is of no use. Official support [here](https://github.com/cloudflare/cf-terraforming).



## Terraflare

Dump Cloudflare configurations in Terraform configuration format.

### HOW TO

1. Setup CloudFlare ID with secret.

    ```sh
    export CLOUDFLARE_EMAIL={{ YOUR-CF-ID }}
    export CLOUDFLARE_TOKEN={{ YOUR-CF-TOKEN }}
    ```

2. Export json for a domain. e.g.,

    ```sh
    # to export DNS Records
    clojure -m terraflare.dns-records ridi.com

    # to export Page Rules
    clojure -m terraflare.pagerules ridi.com
    ```

3. Go [hcl2json](https://www.hcl2json.com/) and get `.hcl` from the output if you want.
