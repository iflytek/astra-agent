import js from '@eslint/js';
import tseslint from '@typescript-eslint/eslint-plugin';
import tsparser from '@typescript-eslint/parser';
import prettier from 'eslint-plugin-prettier';
import eslintConfigPrettier from 'eslint-config-prettier';

export default [
  js.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsparser,
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: 'module',
        project: './tsconfig.json',
      },
      globals: {
        console: 'readonly',
        process: 'readonly',
        Buffer: 'readonly',
        __dirname: 'readonly',
        __filename: 'readonly',
        global: 'readonly',
        module: 'readonly',
        require: 'readonly',
        exports: 'readonly',
        document: 'readonly',
        window: 'writable', // 允许修改 window（如 window.xxx = 123）
        navigator: 'readonly',
        localStorage: 'readonly',
        sessionStorage: 'readonly',
        setTimeout: 'readonly',
        setInterval: 'readonly',
        clearTimeout: 'readonly',
        clearInterval: 'readonly',
        IFlyCollector: 'readonly',
        fetch: 'readonly',
      },
    },
    extends: [eslintConfigPrettier],
    plugins: {
      '@typescript-eslint': tseslint,
      prettier: prettier,
    },
    rules: {
      'no-unused-vars': 'off', // 禁用原生规则
      // Prettier 集成（覆盖为 error，并显式使用 .prettierrc）
      'prettier/prettier': ['warn', {}, { usePrettierrc: true }],
      // TypeScript基本规则
      // TODO: refactor 暂时改成warn
      '@typescript-eslint/no-explicit-any': 'warn',
      // TODO: refactor 暂时改成warn
      '@typescript-eslint/explicit-function-return-type': 'warn',
      // TODO: refactor 暂时改成warn
      '@typescript-eslint/no-unused-vars': [
        'warn',
        {
          vars: 'all',
          args: 'none', // function arguments should not force to match this rule.
          argsIgnorePattern: '^_', // Specifications allow underlining
          ignoreRestSiblings: true, //Use rest syntax (such as'var {foo,... rest} = data ') to ignore foo.
          destructuredArrayIgnorePattern: '^_', //Structural arrays allow _
          caughtErrors: 'none',
          // "caughtErrorsIgnorePattern": "^e$"
        },
      ],
      // TODO: refactor 暂时改成warn
      '@typescript-eslint/no-non-null-assertion': 'warn',
      // 代码复杂度控制
      // TODO: refactor 暂时改成20
      complexity: ['warn', 40],
      // TODO: refactor 暂时改成200
      'max-lines-per-function': [
        'warn',
        {
          max: 200,
          IIFEs: true,
        },
      ],
      'max-params': ['warn', 5],
      // TODO: refactor 暂时改成warn
      'no-extra-boolean-cast': 'warn',
      'no-console': 'warn',
      'no-debugger': 'warn',
      'prefer-const': 'warn',
      'no-var': 'warn',
    },
  },
  {
    ignores: [
      'node_modules/',
      'dist/',
      'build/',
      'coverage/',
      '*.log',
      '.DS_Store',
    ],
  },
];
