import xml.etree.ElementTree as ET
import glob
import re
import os

ns = {'m': 'http://maven.apache.org/POM/4.0.0'}

properties = {}

def resolve(value, props):
    if not value:
        return value
    pattern = re.compile(r'\$\{([^}]+)\}')
    def repl(m):
        key = m.group(1)
        return props.get(key, properties.get(key, m.group(0)))
    return pattern.sub(repl, value)

# Collect all properties from all poms
for path in glob.glob('**/pom.xml', recursive=True):
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    root = tree.getroot()
    props_node = root.find('m:properties', ns)
    if props_node is not None:
        for child in list(props_node):
            key = child.tag.replace('{%s}' % ns['m'], '')
            properties[key] = child.text.strip() if child.text else ''

# Emit dependencies
seen = set()
for path in glob.glob('**/pom.xml', recursive=True):
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        continue
    root = tree.getroot()
    props = {}
    props_node = root.find('m:properties', ns)
    if props_node is not None:
        for child in list(props_node):
            key = child.tag.replace('{%s}' % ns['m'], '')
            props[key] = child.text.strip() if child.text else ''

    for dep in root.findall('.//m:dependency', ns):
        gid = dep.find('m:groupId', ns)
        aid = dep.find('m:artifactId', ns)
        ver = dep.find('m:version', ns)
        if gid is None or aid is None:
            continue
        gid_text = gid.text.strip() if gid.text else ''
        aid_text = aid.text.strip() if aid.text else ''
        version = ''
        if ver is not None and ver.text:
            version = resolve(ver.text.strip(), props)
        coord = f'{gid_text}:{aid_text}:{version}'
        if coord not in seen:
            seen.add(coord)
            print(coord)
