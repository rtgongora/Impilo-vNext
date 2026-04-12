#!/usr/bin/env node
/**
 * Adds spring-boot-starter-kafka after spring-boot-starter-web when the POM has neither
 * spring-kafka nor kafka-clients (declares the service can participate in the platform bus).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVICES = path.resolve(__dirname, '../../services');

const KAFKA_BLOCK = `        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>`;

const WEB_DEP_RE =
  /(\n\s*<dependency>\s*\n\s*<groupId>org\.springframework\.boot<\/groupId>\s*\n\s*<artifactId>spring-boot-starter-web<\/artifactId>\s*\n\s*<\/dependency>)/;

function main() {
  let updated = 0;
  for (const name of fs.readdirSync(SERVICES)) {
    const pom = path.join(SERVICES, name, 'pom.xml');
    if (!fs.existsSync(pom)) continue;
    let t = fs.readFileSync(pom, 'utf8');
    if (!t.includes('spring-boot-starter-web')) continue;
    if (t.includes('spring-kafka') || t.includes('kafka-clients')) continue;
    if (!WEB_DEP_RE.test(t)) continue;
    t = t.replace(WEB_DEP_RE, (m) => m + '\n' + KAFKA_BLOCK);
    fs.writeFileSync(pom, t, 'utf8');
    console.log('spring-kafka:', name);
    updated++;
  }
  console.log('add-spring-kafka-to-service-poms: updated', updated);
}

main();
