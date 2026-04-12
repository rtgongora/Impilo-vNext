#!/usr/bin/env node
/**
 * Adds springdoc-openapi-starter-webmvc-ui after spring-boot-starter-web in each service module pom.xml
 * when springdoc is not already declared.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVICES = path.resolve(__dirname, '../../services');

const SPRINGDOC_BLOCK = `        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>`;

const WEB_DEP_RE =
  /(\n\s*<dependency>\s*\n\s*<groupId>org\.springframework\.boot<\/groupId>\s*\n\s*<artifactId>spring-boot-starter-web<\/artifactId>\s*\n\s*<\/dependency>)/;

function main() {
  let updated = 0;
  for (const name of fs.readdirSync(SERVICES)) {
    const pom = path.join(SERVICES, name, 'pom.xml');
    if (!fs.existsSync(pom)) continue;
    let t = fs.readFileSync(pom, 'utf8');
    if (!t.includes('spring-boot-starter-web') || t.includes('springdoc-openapi')) continue;
    if (!WEB_DEP_RE.test(t)) continue;
    t = t.replace(WEB_DEP_RE, (m) => m + '\n' + SPRINGDOC_BLOCK);
    fs.writeFileSync(pom, t, 'utf8');
    console.log('springdoc:', name);
    updated++;
  }
  console.log('add-springdoc-to-service-poms: updated', updated);
}

main();
