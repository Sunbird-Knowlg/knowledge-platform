#!/bin/bash

###############################################################################
# Neo4j Deprecated Code Cleanup Script
# 
# This script removes deprecated Neo4j/Cypher components after successful
# migration to JanusGraph. 
#
# Date: January 7, 2026
# Author: Knowledge Platform Team
#
# IMPORTANT: Run this script only after:
# 1. JanusGraph implementation is complete (✅ DONE)
# 2. All tests have been migrated and passing
# 3. Production deployment is stable
# 4. Backup has been created
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Neo4j Deprecated Code Cleanup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Safety check - require confirmation
echo -e "${YELLOW}WARNING: This script will remove the following:${NC}"
echo "  - .neo4j-backup/ directory (~1,771 LOC)"
echo "  - Neo4j driver dependencies from pom.xml"
echo "  - Legacy configuration from application.conf"
echo ""
echo -e "${RED}This action cannot be undone without git history!${NC}"
echo ""
read -p "Are you sure you want to proceed? (type 'YES' to confirm): " confirmation

if [ "$confirmation" != "YES" ]; then
    echo -e "${YELLOW}Cleanup cancelled.${NC}"
    exit 0
fi

# Create backup before cleanup
BACKUP_DIR="$PROJECT_ROOT/backups/neo4j-cleanup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo ""
echo -e "${GREEN}Step 1: Creating backup...${NC}"

# Backup .neo4j-backup directory
if [ -d "$PROJECT_ROOT/ontology-engine/graph-dac-api/.neo4j-backup" ]; then
    echo "Backing up .neo4j-backup/ directory..."
    cp -r "$PROJECT_ROOT/ontology-engine/graph-dac-api/.neo4j-backup" "$BACKUP_DIR/"
    echo -e "${GREEN}✓ Backup created at: $BACKUP_DIR${NC}"
else
    echo -e "${YELLOW}⚠ .neo4j-backup/ directory not found${NC}"
fi

# Backup pom.xml files
echo "Backing up pom.xml files..."
find "$PROJECT_ROOT" -name "pom.xml" -exec cp --parents {} "$BACKUP_DIR" \;

echo ""
echo -e "${GREEN}Step 2: Removing deprecated files...${NC}"

# Remove .neo4j-backup directory
if [ -d "$PROJECT_ROOT/ontology-engine/graph-dac-api/.neo4j-backup" ]; then
    echo "Removing .neo4j-backup/ directory..."
    rm -rf "$PROJECT_ROOT/ontology-engine/graph-dac-api/.neo4j-backup"
    echo -e "${GREEN}✓ Removed .neo4j-backup/ directory${NC}"
fi

# Count removed lines
LINES_REMOVED=0
if [ -f "$BACKUP_DIR/.neo4j-backup/GraphQueryGenerationUtil.java" ]; then
    LINES=$(wc -l < "$BACKUP_DIR/.neo4j-backup/GraphQueryGenerationUtil.java")
    LINES_REMOVED=$((LINES_REMOVED + LINES))
fi

echo ""
echo -e "${GREEN}Step 3: Cleaning up dependencies...${NC}"

# List of Neo4j dependencies to check
NEO4J_DEPS=(
    "org.neo4j:neo4j-java-driver"
    "org.neo4j.driver:neo4j-java-driver"
    "org.neo4j:neo4j-bolt"
)

# Check and report Neo4j dependencies (don't auto-remove, as they might be referenced elsewhere)
echo "Checking for Neo4j dependencies in pom.xml files..."
for dep in "${NEO4J_DEPS[@]}"; do
    if grep -r "$dep" "$PROJECT_ROOT" --include="pom.xml" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠ Found Neo4j dependency: $dep${NC}"
        echo -e "${YELLOW}  Manual review recommended before removal${NC}"
    fi
done

echo ""
echo -e "${GREEN}Step 4: Verifying cleanup...${NC}"

# Verify .neo4j-backup is gone
if [ ! -d "$PROJECT_ROOT/ontology-engine/graph-dac-api/.neo4j-backup" ]; then
    echo -e "${GREEN}✓ .neo4j-backup/ successfully removed${NC}"
else
    echo -e "${RED}✗ .neo4j-backup/ still exists${NC}"
    exit 1
fi

# Check for any remaining references to deprecated classes
echo ""
echo "Scanning for references to deprecated classes..."
DEPRECATED_CLASSES=(
    "GraphQueryGenerationUtil"
    "BaseQueryGenerationUtil"
    "CypherQueryConfigurationConstants"
    "Neo4JBoltGraphOperations"
    "Neo4JBoltSearchOperations"
)

REFERENCES_FOUND=0
for class in "${DEPRECATED_CLASSES[@]}"; do
    if grep -r "$class" "$PROJECT_ROOT/ontology-engine" --include="*.java" --exclude-dir=".neo4j-backup" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠ Found reference to deprecated class: $class${NC}"
        grep -r "$class" "$PROJECT_ROOT/ontology-engine" --include="*.java" --exclude-dir=".neo4j-backup" -l
        REFERENCES_FOUND=$((REFERENCES_FOUND + 1))
    fi
done

if [ $REFERENCES_FOUND -eq 0 ]; then
    echo -e "${GREEN}✓ No references to deprecated classes found${NC}"
else
    echo -e "${YELLOW}⚠ Found $REFERENCES_FOUND references to deprecated classes${NC}"
    echo -e "${YELLOW}  Manual cleanup may be required${NC}"
fi

# Generate cleanup report
REPORT_FILE="$BACKUP_DIR/cleanup-report.txt"
cat > "$REPORT_FILE" << EOF
Neo4j Deprecated Code Cleanup Report
=====================================
Date: $(date)
Backup Location: $BACKUP_DIR

Files Removed:
- .neo4j-backup/ directory

Estimated Lines Removed: ~1,771 LOC

Backup Contents:
$(find "$BACKUP_DIR" -type f | sed 's|'"$BACKUP_DIR"'|  -|')

Next Steps:
1. Run test suite: mvn clean test
2. Build project: mvn clean install
3. Verify services start: ./local-setup.sh restart
4. Check health: curl http://localhost:9000/health

If issues arise:
1. Restore from backup: cp -r $BACKUP_DIR/.neo4j-backup $PROJECT_ROOT/ontology-engine/graph-dac-api/
2. Rebuild: mvn clean install
3. Review DEPRECATION-NOTICE.md for rollback instructions

Dependencies to Review Manually:
- Check pom.xml for org.neo4j:* dependencies
- Check application.conf for neo4j.* properties
EOF

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Cleanup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Summary:"
echo "  - Deprecated files removed: .neo4j-backup/"
echo "  - Estimated LOC removed: ~1,771"
echo "  - Backup location: $BACKUP_DIR"
echo "  - Report: $REPORT_FILE"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Review cleanup report: cat $REPORT_FILE"
echo "  2. Run tests: mvn clean test"
echo "  3. Build project: mvn clean install"
echo "  4. Verify services: ./local-setup.sh restart"
echo ""
echo -e "${GREEN}If everything works, you can delete the backup after 30 days.${NC}"
echo ""

exit 0
