.PHONY: build # always run

build:
	set -e

	mvn --version

	echo -e "☕️👷‍♂️Java Build"
	mvn -B clean install -fae -Dno-format

	echo -e "🚀👷‍♂️Native Build"
	mvn -B install -ff -Dnative -Dquarkus.native.container-build -Dnative.surefire.skip

	echo -e "🖼Building Sample Projects"
	set -e
	pids=()
	for dir in samples/*/ ; do
	  (cd "$dir" && ./mvnw -B -ff verify) &
	  pids+=($!)
	done

	failure=0
	for pid in "${pids[@]}"; do
	  wait "$pid" || failure=1
	done

	if [ $failure -ne 0 ]; then
	  echo "At least one sample build failed."
	  exit 1
	fi

