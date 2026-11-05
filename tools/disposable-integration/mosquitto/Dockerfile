FROM eclipse-mosquitto:2

# The official image contains the Dynamic Security plugin in the standard
# location used by the project. Fail during image construction if it is absent
# rather than silently starting an unauthorised broker.
RUN test -f /usr/lib/mosquitto_dynamic_security.so && mosquitto_ctrl dynsec --help >/dev/null 2>&1

COPY entrypoint.sh /usr/local/bin/nexaiot-b46-entrypoint.sh
RUN chmod 0755 /usr/local/bin/nexaiot-b46-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/nexaiot-b46-entrypoint.sh"]
