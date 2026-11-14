const fs = require('fs');
const f = '.github/workflows/build.yml';
let s = fs.readFileSync(f, 'utf8');

const oldBlock = [
  '      - name: 失败时提取编译错误',
  '        if: failure()',
  '        run: |',
  '          if [ -f build.log ]; then',
  '            echo "### 编译/构建错误" >> $GITHUB_STEP_SUMMARY',
  '            grep -E "^e: |error: |What went wrong" build.log | head -40 >> $GITHUB_STEP_SUMMARY || echo "(未匹配到具体错误行,请展开上面的构建步骤查看完整日志)" >> $GITHUB_STEP_SUMMARY',
  '            grep -E "^e: |error: " build.log | head -40 > ci-errors.txt || true',
  '            if [ -s ci-errors.txt ]; then',
  '              git config user.name "github-actions[bot]"',
  '              git config user.email "41898282+github-actions[bot]@users.noreply.github.com"',
  '              git add ci-errors.txt',
  '              git commit -m "ci: 自动记录编译错误(供开发排查)"',
  '              git push',
  '            fi',
  '          fi',
].join('\n');

const newBlock = [
  '      - name: 失败时输出诊断信息',
  '        if: failure()',
  '        run: |',
  '          if [ -f build.log ]; then',
  '            echo "### 构建日志尾部 45 行" >> $GITHUB_STEP_SUMMARY',
  '            tail -n 45 build.log >> $GITHUB_STEP_SUMMARY',
  '            TAIL=$(tail -n 45 build.log)',
  '            TESC=$(echo "$TAIL" | sed \':a;N;$!ba;s/\\n/%0A/g\')',
  '            echo "::error::构建失败,日志尾部:$TESC"',
  '          fi',
].join('\n');

if (!s.includes(oldBlock)) { console.log('提取 step 未匹配!'); process.exit(1); }
s = s.replace(oldBlock, newBlock);
fs.writeFileSync(f, s);
console.log('诊断改造完成');
