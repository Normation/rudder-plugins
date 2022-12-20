const fs = require('fs');
const path = require('path');
const { watch, series, parallel, src, dest } = require('gulp');
const rename = require('gulp-rename');
const mode = require('gulp-mode');
const profile = mode();
const terser = require('gulp-terser');
const elm_p = require('gulp-elm');
const grep = require('gulp-grep-contents');
const merge = require('merge-stream');
const del = require('del');

const paths = {
    'elm': {
        'src': 'elm',
        'watch': 'elm/sources/*.elm',
        'dest': 'elm/generated',
    },
};

function clean(cb) {
    del.sync([paths.elm.dest]);
    cb();
}

function elm(cb) {
    src(path.join(paths.elm.watch))
        // Detect entry points
        .pipe(grep(/Browser.element/))
        .pipe(elm_p({
            optimize: profile.production(),
            cwd: path.join(paths.elm.src),
        }))
        .pipe(rename(function (path) {
            return {
                dirname: '',
                basename: 'rudder-' + path.basename.toLowerCase(),
                extname: '.js'
            };
        }))
        // elm minification options from https://guide.elm-lang.org/optimization/asset_size.html#instructions
        .pipe(profile.production(terser({
            compress: {
                pure_funcs: ['F2', 'F3', 'F4', 'F5', 'F6', 'F7', 'F8', 'F9', 'A2', 'A3', 'A4',
                    'A5', 'A6', 'A7', 'A8', 'A9'
                ],
                pure_getters: true,
                keep_fargs: false,
                unsafe_comps: true,
                unsafe: true,
            },
        })))
        .pipe(profile.production(terser({
            mangle: true,
        })))
        .pipe(dest(paths.elm.dest));
    cb();
};

exports.elm = series(clean, elm)
exports.watch = series(clean, function() {
    watch(paths.elm.watch, { ignoreInitial: false }, elm);
});
exports.default = series(clean, parallel(elm));
