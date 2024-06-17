import gulp from 'gulp';
const { task, watch, series, parallel, src, dest } = gulp;
import fs from 'fs';
import path from 'path';
import rename from 'gulp-rename';
import mode from 'gulp-mode';
const profile = mode();
import terser from 'gulp-terser';
import elm_p from 'gulp-elm';
import { deleteSync } from 'del';
import through from 'through2';
import * as dartSass from 'sass';
import gulpSass from 'gulp-sass';
const sass = gulpSass(dartSass);
import sourcemaps from 'gulp-sourcemaps';

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

const __dirname = path.resolve();

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
    let mergeCleanPaths = function(){
        let getCleanPaths = function(p, extension){
            let files  = (p + '/**');
            if (!!extension) {
              files = files + extension;
	    }
            let parent = ('!' + p);
            return [files, parent];
        }
        return [].concat(
            getCleanPaths(paths.elm.dest),
            getCleanPaths(gestScssDest(__dirname), 'css')
        );
    }
    let cleanPaths = mergeCleanPaths();
    deleteSync(cleanPaths);
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

    src(paths.scss.src, { encoding: false })
      .pipe(sourcemaps.init())
      .pipe(sass({outputStyle: 'compressed'}).on('error', sass.logError))
      .pipe(sourcemaps.write())
      .pipe(dest(gestScssDest(__dirname)));
    cb();
};

task('elm', series(clean, elm));

task('watch', series(clean, function() {
    watch(paths.elm.watch, { ignoreInitial: false }, elm);
    watch(paths.scss.src, { ignoreInitial: false }, scss);
}));

task('default', series(clean, parallel(elm, scss)));
