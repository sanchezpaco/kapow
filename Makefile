JAVA_HOME := /Applications/Android Studio.app/Contents/jbr/Contents/Home
SDK := $(HOME)/Library/Android/sdk
ADB := $(SDK)/platform-tools/adb
GRADLEW := ./gradlew
APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk
BUNDLE := app/build/outputs/bundle/release/app-release.aab
BUNDLE_APKS := app/build/outputs/bundle/release/app-release.apks
BUNDLETOOL := $(HOME)/.android/tools/bundletool.jar
JAVA := $(JAVA_HOME)/bin/java
PLAY_JSON_KEY := $(shell sed -n 's/^kapow.play.serviceAccountJson=//p' local.properties)
APP_ID := com.sanchezpaco.kapow.debug
BUILD_LABEL := $(shell date +'%Y-%m-%d %H:%M') $(shell git rev-parse --short HEAD)

export JAVA_HOME

.PHONY: help build test install run deploy devices clean release install-release deploy-release bundle deploy-bundle publish-internal

help:
	@echo "Kapow build targets:"
	@echo "  make build          - assemble the debug APK"
	@echo "  make test           - run the unit tests"
	@echo "  make install        - install the debug APK on a device"
	@echo "  make run            - launch the app on the device"
	@echo "  make deploy         - build, install and run (debug)"
	@echo "  make release        - assemble the release APK signed with the upload key"
	@echo "  make install-release- install the release APK on a device"
	@echo "  make deploy-release - build and install the release APK"
	@echo "  make bundle         - build the signed release bundle (AAB)"
	@echo "  make deploy-bundle  - build the AAB, derive its universal APK, install it"
	@echo "  make publish-internal - upload the AAB to the Play internal testing track"
	@echo "  make devices        - list connected devices"
	@echo "  make clean          - gradle clean"
	@echo ""
	@echo "Pick a device explicitly with DEVICE=<serial>, e.g.:"
	@echo "  make install DEVICE=emulator-5554"

build:
	$(GRADLEW) :app:assembleDebug -PbuildLabel="$(BUILD_LABEL)"

test:
	$(GRADLEW) :app:testDebugUnitTest

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
	$(GRADLEW) :app:assembleRelease -PbuildLabel="$(BUILD_LABEL)"

install-release: release
	@serial=$$($(MAKE) -s pick-device); \
	echo "Installing release on $$serial..."; \
	"$(ADB)" -s "$$serial" install -r -d "$(RELEASE_APK)"

deploy-release: install-release

bundle:
	$(GRADLEW) :app:bundleRelease -PbuildLabel="$(BUILD_LABEL)"

deploy-bundle: bundle
	@serial=$$($(MAKE) -s pick-device); \
	rm -f "$(BUNDLE_APKS)"; \
	"$(JAVA)" -jar "$(BUNDLETOOL)" build-apks --bundle="$(BUNDLE)" --output="$(BUNDLE_APKS)" --mode=universal; \
	echo "Installing bundle-derived APK on $$serial..."; \
	"$(JAVA)" -jar "$(BUNDLETOOL)" install-apks --apks="$(BUNDLE_APKS)" --device-id="$$serial" --adb="$(ADB)"

publish-internal: bundle
	fastlane supply --aab "$(BUNDLE)" --track internal --json_key "$(PLAY_JSON_KEY)" \
		--package_name com.sanchezpaco.kapow \
		--skip_upload_metadata --skip_upload_images --skip_upload_screenshots

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
		echo "No single device connected; pass DEVICE=<serial> (see make devices)" >&2; exit 1; \
	fi
