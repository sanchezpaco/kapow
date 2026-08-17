JAVA_HOME := /Applications/Android Studio.app/Contents/jbr/Contents/Home
SDK := $(HOME)/Library/Android/sdk
ADB := $(SDK)/platform-tools/adb
BUILD_TOOLS := $(SDK)/build-tools/36.0.0
GRADLEW := ./gradlew
APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_UNSIGNED := app/build/outputs/apk/release/app-release-unsigned.apk
RELEASE_APK := app/build/outputs/apk/release/app-release-signed.apk
DEBUG_KEYSTORE := $(HOME)/.android/debug.keystore
APP_ID := com.comicify.debug
DEFAULT_DEVICE := R3GL60C82WD

export JAVA_HOME

.PHONY: help build install run deploy devices clean release install-release deploy-release

help:
	@echo "Comicify build targets:"
	@echo "  make build          - assemble the debug APK"
	@echo "  make install        - install the debug APK on a device"
	@echo "  make run            - launch the app on the device"
	@echo "  make deploy         - build, install and run (debug)"
	@echo "  make release        - assemble and sign the release APK (debug key)"
	@echo "  make install-release- install the signed release APK on a device"
	@echo "  make deploy-release - build, sign and install the release APK"
	@echo "  make devices        - list connected devices"
	@echo "  make clean          - gradle clean"
	@echo ""
	@echo "Pick a device explicitly with DEVICE=<serial>, e.g.:"
	@echo "  make install DEVICE=$(DEFAULT_DEVICE)"

build:
	$(GRADLEW) :app:assembleDebug

install: build
	@serial=$$($(MAKE) -s pick-device); \
	echo "Installing on $$serial..."; \
	"$(ADB)" -s "$$serial" install -r "$(APK)"

run:
	@serial=$$($(MAKE) -s pick-device); \
	echo "Launching on $$serial..."; \
	"$(ADB)" -s "$$serial" shell monkey -p $(APP_ID) -c android.intent.category.LAUNCHER 1 >/dev/null

deploy: install run

release:
	$(GRADLEW) :app:assembleRelease
	"$(BUILD_TOOLS)/zipalign" -f -p 4 "$(RELEASE_UNSIGNED)" "$(RELEASE_UNSIGNED).aligned"
	"$(BUILD_TOOLS)/apksigner" sign \
		--ks "$(DEBUG_KEYSTORE)" --ks-key-alias androiddebugkey \
		--ks-pass pass:android --key-pass pass:android \
		--out "$(RELEASE_APK)" "$(RELEASE_UNSIGNED).aligned"
	@rm -f "$(RELEASE_UNSIGNED).aligned"

install-release: release
	@serial=$$($(MAKE) -s pick-device); \
	echo "Installing release on $$serial..."; \
	"$(ADB)" -s "$$serial" install -r -d "$(RELEASE_APK)"

deploy-release: install-release

devices:
	@"$(ADB)" devices | sed '1d;/^$$/d'

clean:
	$(GRADLEW) clean

pick-device:
	@devices=$$("$(ADB)" devices | sed '1d;/^$$/d' | grep -w device | cut -f1); \
	count=$$(echo "$$devices" | grep -c .); \
	if [ -n "$(DEVICE)" ]; then \
		echo "$(DEVICE)"; \
	elif [ "$$count" -eq 1 ]; then \
		echo "$$devices"; \
	elif [ "$$count" -gt 1 ] && [ -t 0 ]; then \
		i=1; \
		for d in $$devices; do echo "  $$i) $$d" >&2; i=$$((i+1)); done; \
		printf "Choose a device [1-%s]: " "$$count" >&2; \
		read choice; \
		echo "$$devices" | sed -n "$${choice}p"; \
	else \
		echo "$(DEFAULT_DEVICE)"; \
	fi
