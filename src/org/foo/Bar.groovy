// https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
@Grab(group='org.apache.commons', module='commons-lang3', version='3.5')
@Grab(group='org.apache.maven', module='maven-model', version='3.3.9')
@Grab(group='commons-lang', module='commons-lang', version='2.6')
@Grab('org.yaml:snakeyaml:1.26')

import org.apache.commons.lang3.StringUtils

def call(String text) {
    def reversedText = StringUtils.reverse(text)
    echo "Reversed text: ${reversedText}"
}