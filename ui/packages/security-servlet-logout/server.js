/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/

var express = require('express'),
  server = require('./server-impl')

var morgan = require('morgan')

var app = express()
// uncomment to get some debugging
//app.use(express.logger());
//enable the live reload
app.use(require('connect-livereload')())

// our compiled css gets moved to /target/webapp/css so use it there
app.use('/logout', express.static(__dirname + '/target/webapp'))
app.use('/logout', express.static(__dirname + '/src/main/webapp'))

//if we're mocking, it is being run by grunt
console.log('setting up proxy only')
app.use(morgan('dev'))
app.all('*', server.requestProxy)

const launcher = app.listen(process.env.PORT || 8282, function() {
  console.log('Server listening on port ' + launcher.address().port)
})
