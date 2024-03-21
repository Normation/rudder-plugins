const fs = require('fs');
const path = require('path');
const { watch, series, parallel, src, dest } = require('gulp');
const rename = require('gulp-rename');
const mode = require('gulp-mode');
const profile = mode();
const terser = require('gulp-terser');
const elm_p = require('gulp-elm');
const merge = require('merge-stream');
const del = require('del');
const through = require('through2');
const sass = require('gulp-sass')(require('sass'));
const sourcemaps = require('gulp-sourcemaps');

// Derived from https://github.com/mixmaxhq/gulp-grep-contents (under MIT License)
var grep = function(regex) {
    var restoreStream = through.obj();
    return through.obj(function(file, encoding, callback) {
        var match = regex.test(String(file.contents))
        if (match) {
            callback(null, file);
            return;
        }
        restoreStream.write(file);
        callback();
    });
}

const paths = {
    'elm': {
        'src': 'elm',
        'watch': 'elm/sources/*.elm',
        'dest': 'elm/generated',
    },
    'scss': {
        'src': [
          'style/*',
        ],
        'dest': 'resources/toserve/',
    },
};

function gestScssDest(dirname){
    let dir = dirname.split(path.sep);
    dir = dir[dir.length - 3].replace('-', '');
    return paths.scss.dest + dir
}

function clean(cb) {
    del.sync([paths.elm.dest]);
    cb();
}

function elm(cb) {
    src(paths.elm.watch)
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

function scss(cb) {
    src(paths.scss.src)
      .pipe(sourcemaps.init())
      .pipe(sass({outputStyle: 'compressed'}).on('error', sass.logError))
      .pipe(sourcemaps.write())
      .pipe(dest(gestScssDest(__dirname)));
    cb();
};

exports.elm = series(clean, elm)
exports.watch = series(clean, function() {
    watch(paths.elm.watch, { ignoreInitial: false }, elm);
    watch(paths.scss.src, { ignoreInitial: false }, scss);
});
exports.default = series(clean, parallel(elm, scss));
